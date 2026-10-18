package io.github.mcheliceport.effect;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Client effects called from common code: upstream's {@code MCH_ParticlesUtil} and the client
 * half of {@code MCH_ExplosionV2}. The client registers its {@link Sink}; on a server, or before
 * the client is up, every call does nothing.
 */
public final class McheliEffects {

    /** What the client draws. Every method is only called on the client thread. */
    public interface Sink {
        /** True replaces a plane's legacy nozzle smoke; false preserves that emitter. */
        default boolean engineExhaust(io.github.mcheliceport.entity.aircraft.AircraftEntity aircraft) { return false; }
        /** True when the client handled enhanced helicopter wash; false keeps the upstream emitter. */
        default boolean rotorWash(io.github.mcheliceport.entity.aircraft.AircraftEntity aircraft, boolean seaOnly) {
            return false;
        }
        /** Upstream {@code MCH_ParticlesUtil#spawnParticle}: a McHeli smoke or splash particle. */
        void spawnParticle(Level level, ParticleParam param);

        /** Upstream {@code spawnParticleMuzzleFlash}. */
        void spawnMuzzleFlash(Level level, double x, double y, double z, float size, float r, float g, float b,
                float a, int age);

        /**
         * Upstream {@code DEF_spawnParticle}: a vanilla particle by its 1.12 name (such as
         * {@code largesmoke}, {@code blockdust_<state>}), within 300 blocks and the particle setting.
         */
        void spawnVanilla(Level level, String name, double x, double y, double z, double mx, double my, double mz);

        /** Upstream {@code spawnParticleTileDust}: dust of the block at or next to a position. */
        boolean spawnTileDust(Level level, int blockX, int blockY, int blockZ, double x, double y, double z,
                double mx, double my, double mz, float scale);

        /** Upstream {@code spawnParticleTileCrack}. */
        void spawnTileCrack(Level level, int blockX, int blockY, int blockZ, double x, double y, double z,
                double mx, double my, double mz);

        /** Upstream {@code PacketParticleEffect#onReceive}: the effects of an explosion. */
        void explosionEffects(Level level, double x, double y, double z, float size, boolean inWater,
                List<BlockPos> affectedBlocks);

        default void explosionEffects(Level level, double x, double y, double z, float size, boolean inWater,
                List<BlockPos> affectedBlocks, BlastStyle style) {
            explosionEffects(level, x, y, z, size, inWater, affectedBlocks);
        }
    }

    private static volatile @Nullable Sink sink;

    private McheliEffects() {
    }

    public static void setSink(@Nullable Sink value) {
        sink = value;
    }

    public static boolean engineExhaust(io.github.mcheliceport.entity.aircraft.AircraftEntity aircraft) {
        Sink s = sink(aircraft.level());
        return s != null && s.engineExhaust(aircraft);
    }

    private static @Nullable Sink sink(Level level) {
        return level.isClientSide() ? sink : null;
    }

    public static boolean rotorWash(io.github.mcheliceport.entity.aircraft.AircraftEntity aircraft, boolean seaOnly) {
        Sink s = sink(aircraft.level());
        return s != null && s.rotorWash(aircraft, seaOnly);
    }

    public static void spawnParticle(Level level, ParticleParam param) {
        Sink s = sink(level);
        if (s != null) {
            s.spawnParticle(level, param);
        }
    }

    public static void spawnMuzzleFlash(Level level, double x, double y, double z, float size, float r, float g,
            float b, float a, int age) {
        Sink s = sink(level);
        if (s != null) {
            s.spawnMuzzleFlash(level, x, y, z, size, r, g, b, a, age);
        }
    }

    public static void spawnVanilla(Level level, String name, double x, double y, double z, double mx, double my,
            double mz) {
        Sink s = sink(level);
        if (s != null) {
            s.spawnVanilla(level, name, x, y, z, mx, my, mz);
        }
    }

    public static boolean spawnTileDust(Level level, int blockX, int blockY, int blockZ, double x, double y,
            double z, double mx, double my, double mz, float scale) {
        Sink s = sink(level);
        return s != null && s.spawnTileDust(level, blockX, blockY, blockZ, x, y, z, mx, my, mz, scale);
    }

    public static void spawnTileCrack(Level level, int blockX, int blockY, int blockZ, double x, double y, double z,
            double mx, double my, double mz) {
        Sink s = sink(level);
        if (s != null) {
            s.spawnTileCrack(level, blockX, blockY, blockZ, x, y, z, mx, my, mz);
        }
    }

    public static void explosionEffects(Level level, double x, double y, double z, float size, boolean inWater,
            List<BlockPos> affectedBlocks) {
        Sink s = sink(level);
        if (s != null) {
            s.explosionEffects(level, x, y, z, size, inWater, affectedBlocks);
        }
    }

    public static void explosionEffects(Level level, double x, double y, double z, float size, boolean inWater,
            List<BlockPos> affectedBlocks, BlastStyle style) {
        Sink s = sink(level);
        if (s != null) s.explosionEffects(level, x, y, z, size, inWater, affectedBlocks, style);
    }
}
