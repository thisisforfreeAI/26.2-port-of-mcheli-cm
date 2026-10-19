package io.github.mcheliceport.wingman.handler;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code ProjectileChunkLoader}: keeps chunks loaded along the flight path of projectiles,
 * so bullets, rockets and missiles keep flying and can hit targets in terrain no player keeps loaded.
 * A projectile does so when its weapon sets {@code CanLoadChunks}, or every projectile does with the
 * server config's {@code projectiles.chunkLoad}.
 *
 * <p>As upstream, chunks are leased: each tick a projectile asks for its own chunk and the line of
 * chunks ahead of it, 4 chunks long plus up to 2 more for fast rounds, and each of those stays
 * loaded until {@code projectiles.chunkLeaseTicks} after the last request, so the shots of a burst
 * share the same chunks instead of loading and unloading them. A sweep after each level tick
 * releases the chunks whose lease has run out. Upstream was bounded by Forge's ticket budget; the
 * port holds at most {@link #MAX_CHUNKS} chunks per level, Forge's default budget of 200 tickets of
 * 25 chunks. Leases are not saved: the tickets are dropped when the world loads again.
 */
public final class ProjectileChunkLoader {

    /** Base length (chunks) of the loaded line ahead of a projectile, including its own chunk. */
    private static final int BASE_LINE_CHUNKS = 4;
    /** Velocity-scaled lookahead: about 8 ticks of travel. */
    private static final int VELOCITY_LEAD_TICKS = 8;
    /** Cap (chunks) on the velocity-scaled extension beyond the base line. */
    private static final int MAX_VELOCITY_EXTRA_CHUNKS = 2;
    /** Step (blocks) used to walk the path and collect the chunks it crosses. */
    private static final double SAMPLE_STEP = 8.0;
    static final int MAX_CHUNKS = 200 * 25;
    /** Owns every projectile ticket: leases are shared, not per projectile. */
    private static final UUID OWNER = UUID.fromString("6d636865-6c69-5072-6f6a-436875636b73");

    public static final TicketController TICKETS = new TicketController(
            Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "projectile"),
            (level, helper) -> helper.getEntityTickets().keySet().forEach(helper::removeAllTickets));

    /** Per level: chunk (as a long) to the game time its lease runs out. */
    private static final Map<ResourceKey<Level>, Long2LongOpenHashMap> LEASES = new HashMap<>();

    private ProjectileChunkLoader() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterTicketControllersEvent e) -> e.register(TICKETS));
        NeoForge.EVENT_BUS.addListener(ProjectileChunkLoader::onLevelTick);
        NeoForge.EVENT_BUS.addListener(ProjectileChunkLoader::onLevelUnload);
    }

    /** Upstream {@code MCH_EntityBaseBullet#isChunkLoadingEnabled}. */
    public static boolean isEnabledFor(@Nullable WeaponInfo info) {
        return info != null && (info.enableChunkLoader
                || ServerConfig.SPEC.isLoaded() && ServerConfig.PROJECTILE_CHUNK_LOAD.get());
    }

    /** Leases the chunks along this projectile's path for this tick, loading those not loaded yet. */
    public static void requestPath(ServerLevel level, BaseBulletEntity bullet) {
        Long2LongOpenHashMap leases = LEASES.computeIfAbsent(level.dimension(), k -> new Long2LongOpenHashMap());
        long until = level.getGameTime() + ServerConfig.PROJECTILE_CHUNK_LEASE_TICKS.get();
        for (long chunk : pathChunks(bullet.getX(), bullet.getZ(), bullet.getDeltaMovement())) {
            if (leases.containsKey(chunk)) {
                if (until > leases.get(chunk)) {
                    leases.put(chunk, until);
                }
                continue;
            }
            if (leases.size() >= MAX_CHUNKS) {
                continue;
            }
            TICKETS.forceChunk(level, OWNER, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true, false);
            leases.put(chunk, until);
        }
    }

    /**
     * After the level's tick, when its projectiles have asked for their paths: releases the chunks
     * whose lease has run out. A chunk asked for this tick is kept even with a lease of 0 ticks, so
     * a projectile always has its chunk loaded when it moves next.
     */
    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            sweep(level);
        }
    }

    static void sweep(ServerLevel level) {
        Long2LongOpenHashMap leases = LEASES.get(level.dimension());
        if (leases == null || leases.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        ObjectIterator<Long2LongMap.Entry> it = leases.long2LongEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2LongMap.Entry e = it.next();
            if (e.getLongValue() >= now) {
                continue;
            }
            long chunk = e.getLongKey();
            it.remove();
            TICKETS.forceChunk(level, OWNER, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false, false);
        }
    }

    /** NeoForge drops an unloading level's tickets; forget its leases. */
    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LEASES.remove(level.dimension());
        }
    }

    /** How many chunks are leased in the level. */
    public static int leased(ServerLevel level) {
        Long2LongOpenHashMap leases = LEASES.get(level.dimension());
        return leases == null ? 0 : leases.size();
    }

    public static boolean isLeased(ServerLevel level, ChunkPos pos) {
        Long2LongOpenHashMap leases = LEASES.get(level.dimension());
        return leases != null && leases.containsKey(pos.pack());
    }

    /**
     * Upstream {@code computePathChunks}: the projectile's chunk, and the chunks on the line ahead of
     * it along its horizontal motion, {@link #BASE_LINE_CHUNKS} long and up to
     * {@link #MAX_VELOCITY_EXTRA_CHUNKS} more with speed.
     */
    static long[] pathChunks(double x, double z, Vec3 motion) {
        it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet chunks = new it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet();
        chunks.add(ChunkPos.pack(floorChunk(x), floorChunk(z)));
        double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (speed >= 1.0e-4) {
            double lead = BASE_LINE_CHUNKS * 16.0 + Math.min(MAX_VELOCITY_EXTRA_CHUNKS * 16.0,
                    speed * VELOCITY_LEAD_TICKS);
            double ux = motion.x / speed;
            double uz = motion.z / speed;
            for (double d = SAMPLE_STEP; d <= lead; d += SAMPLE_STEP) {
                chunks.add(ChunkPos.pack(floorChunk(x + ux * d), floorChunk(z + uz * d)));
            }
        }
        return chunks.toLongArray();
    }

    private static int floorChunk(double coordinate) {
        return (int) Math.floor(coordinate / 16.0);
    }
}
