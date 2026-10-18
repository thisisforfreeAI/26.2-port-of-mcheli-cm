package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import com.mojang.serialization.JsonOps;
import io.github.mcheliceport.net.MarkerUpdatePayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.registry.ModBlocks;
import io.github.mcheliceport.wingman.block.MarkerType;
import io.github.mcheliceport.wingman.block.WingmanMarkerBlock;
import io.github.mcheliceport.wingman.block.WingmanMarkerBlockEntity;
import io.github.mcheliceport.wingman.registry.MarkerRegistry;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Upstream's wingman markers: the block entity, {@code MarkerRegistry} and {@code PacketMarkerUpdate}. */
final class WingmanMarkerGameTests {

    private WingmanMarkerGameTests() {
    }

    static void register() {
        ModGameTests.test("wingman_marker_takes_an_id_and_registers", 20, WingmanMarkerGameTests::placement);
        ModGameTests.test("wingman_marker_update_applies_within_reach", 20, WingmanMarkerGameTests::update);
        ModGameTests.test("wingman_marker_registry_resolves_bases", 20, WingmanMarkerGameTests::resolve);
    }

    private static MarkerRegistry.@Nullable MarkerInfo recorded(ServerLevel level, BlockPos pos) {
        for (MarkerRegistry.MarkerInfo m : MarkerRegistry.snapshot(level)) {
            if (m.pos().equals(pos)) {
                return m;
            }
        }
        return null;
    }

    /** A new marker is a parking spot with a {@code p_} ID in the registry, and leaves it when broken. */
    private static void placement(GameTestHelper h) {
        BlockPos rel = new BlockPos(2, 1, 2);
        BlockPos pos = h.absolutePos(rel);
        h.setBlock(rel, ModBlocks.WINGMAN_MARKER.get().defaultBlockState());
        h.runAfterDelay(2, () -> {
            check(h, h.getBlockState(rel).getValue(WingmanMarkerBlock.TYPE) == MarkerType.PARKING,
                    "a new marker is not a parking spot");
            WingmanMarkerBlockEntity marker = h.getBlockEntity(rel, WingmanMarkerBlockEntity.class);
            check(h, marker.getMarkerId().startsWith("p_"), "the marker took no parking ID: " + marker.getMarkerId());
            MarkerRegistry.MarkerInfo info = recorded(h.getLevel(), pos);
            check(h, info != null && info.id().equals(marker.getMarkerId()), "the marker is not registered");
            h.setBlock(rel, Blocks.AIR);
            check(h, recorded(h.getLevel(), pos) == null, "the broken marker is still registered");
            // The /setblock command skips the block's removal hooks.
            h.getLevel().setBlock(pos, ModBlocks.WINGMAN_MARKER.get().defaultBlockState(), 3);
            if (h.getLevel().getBlockEntity(pos) instanceof WingmanMarkerBlockEntity again) {
                MarkerRegistry.register(h.getLevel(), again.info());
            }
            check(h, recorded(h.getLevel(), pos) != null, "the second marker is not registered");
            h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS
                    | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
            check(h, recorded(h.getLevel(), pos) == null, "a marker replaced as /setblock does is still registered");
            h.succeed();
        });
    }

    /**
     * The screen's settings apply from within reach: a base drops its parent base, only a parking
     * spot keeps a heading, and the type goes to the block state. Out of reach nothing changes.
     */
    private static void update(GameTestHelper h) {
        BlockPos rel = new BlockPos(2, 1, 2);
        BlockPos pos = h.absolutePos(rel);
        h.setBlock(rel, ModBlocks.WINGMAN_MARKER.get().defaultBlockState());
        Player near = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(3.5, 1, 3.5));
        Player far = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(3.5, 1, 40.5));
        h.runAfterDelay(2, () -> {
            WingmanMarkerBlockEntity marker = h.getBlockEntity(rel, WingmanMarkerBlockEntity.class);
            check(h, !marker.getMarkerId().isEmpty(), "the marker has no ID yet");
            ModNetwork.applyMarkerUpdate(far, new MarkerUpdatePayload(pos, MarkerType.RUNWAY_A, "far", "x", 1));
            check(h, !marker.getMarkerId().equals("far")
                    && marker.getMarkerType() == MarkerType.PARKING, "an update applied out of reach");

            ModNetwork.applyMarkerUpdate(near, new MarkerUpdatePayload(pos, MarkerType.RUNWAY_A, " ra_alpha ", "alpha", 2));
            check(h, h.getBlockState(rel).getValue(WingmanMarkerBlock.TYPE) == MarkerType.RUNWAY_A,
                    "the block state kept the old type");
            check(h, marker.getMarkerId().equals("ra_alpha") && marker.getBaseId().equals("alpha")
                    && marker.getParkingHeading() == -1, "the runway end's settings are wrong");
            MarkerRegistry.MarkerInfo info = recorded(h.getLevel(), pos);
            check(h, info != null && info.type() == MarkerType.RUNWAY_A && info.baseId().equals("alpha"),
                    "the registry kept the old settings");

            ModNetwork.applyMarkerUpdate(near, new MarkerUpdatePayload(pos, MarkerType.PARKING, "p_alpha", "alpha", 3));
            check(h, marker.getParkingHeading() == 3, "the parking spot lost its heading");
            ModNetwork.applyMarkerUpdate(near, new MarkerUpdatePayload(pos, MarkerType.BASE, "alpha", "beta", 3));
            check(h, marker.getBaseId().isEmpty() && marker.getParkingHeading() == -1,
                    "the base kept a parent base or heading");

            var buf = new FriendlyByteBuf(Unpooled.buffer());
            var sent = new MarkerUpdatePayload(pos, MarkerType.HELIPAD_B, "hpb_1", "alpha", -1);
            MarkerUpdatePayload.CODEC.encode(buf, sent);
            check(h, MarkerUpdatePayload.CODEC.decode(buf).equals(sent), "the payload changed in its codec");
            h.setBlock(rel, Blocks.AIR);
            h.succeed();
        });
    }

    /** Upstream {@code resolveMarker}: a base's child of the type, else a marker with that ID; the save keeps both. */
    private static void resolve(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos base = h.absolutePos(new BlockPos(1, 1, 1));
        BlockPos runway = h.absolutePos(new BlockPos(1, 1, 3));
        BlockPos lone = h.absolutePos(new BlockPos(1, 1, 5));
        MarkerRegistry.register(level, new MarkerRegistry.MarkerInfo(base, MarkerType.BASE, "resolve_base", "", -1));
        MarkerRegistry.register(level, new MarkerRegistry.MarkerInfo(runway, MarkerType.RUNWAY_A, "ra_9", "resolve_base", -1));
        MarkerRegistry.register(level, new MarkerRegistry.MarkerInfo(lone, MarkerType.RUNWAY_A, "resolve_lone", "", -1));
        MarkerRegistry.MarkerInfo child = MarkerRegistry.resolveMarker(level, "resolve_base", MarkerType.RUNWAY_A);
        check(h, child != null && child.pos().equals(runway), "the base did not resolve to its runway end");
        MarkerRegistry.MarkerInfo direct = MarkerRegistry.resolveMarker(level, "resolve_lone", MarkerType.RUNWAY_A);
        check(h, direct != null && direct.pos().equals(lone), "the ID did not resolve to its marker");
        check(h, MarkerRegistry.findChildren(level, "resolve_base").size() == 1, "the base has the wrong children");

        var markers = List.of(MarkerRegistry.findById(level, MarkerType.BASE, "resolve_base"), child);
        var json = MarkerRegistry.MarkerInfo.CODEC.listOf().encodeStart(JsonOps.INSTANCE, markers).getOrThrow();
        check(h, MarkerRegistry.MarkerInfo.CODEC.listOf().parse(JsonOps.INSTANCE, json).getOrThrow().equals(markers),
                "the saved markers changed");
        for (BlockPos pos : List.of(base, runway, lone)) {
            MarkerRegistry.unregister(level, pos);
        }
        h.succeed();
    }
}
