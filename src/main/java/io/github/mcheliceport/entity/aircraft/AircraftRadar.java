package io.github.mcheliceport.entity.aircraft;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The entity radar of vehicles with {@code EnableEntityRadar}: every 72 ticks the rider's client
 * lists the mobs within 64 blocks, horizontally, that are not under five or more blocks. Ported
 * from upstream's {@code MCH_Radar} and {@code MCH_EntityAircraft#updateRadar}.
 */
public final class AircraftRadar {

    /** Upstream's radar range in blocks. */
    public static final int RANGE = 64;
    /** Upstream scans up to this height for cover. */
    private static final int COVER_SCAN_TOP = 200;

    private int radarRotate;
    /** Offsets (x, z) of the mobs that are not monsters, in pairs. */
    private double[] entities = new double[0];
    /** Offsets (x, z) of the monsters, in pairs. */
    private double[] enemies = new double[0];

    public int getRadarRotate() {
        return this.radarRotate;
    }

    public double[] entities() {
        return this.entities;
    }

    public double[] enemies() {
        return this.enemies;
    }

    /** Upstream {@code initRadar}. */
    public void clear() {
        this.entities = new double[0];
        this.enemies = new double[0];
        this.radarRotate = 0;
    }

    /** Upstream {@code updateRadar(radarSpeed)}: turns the sweep and scans when it passes zero. */
    public void update(Entity center, int radarSpeed) {
        this.radarRotate += radarSpeed;
        if (this.radarRotate >= 360) {
            this.radarRotate = 0;
        }
        if (this.radarRotate == 0) {
            this.scan(center, RANGE);
        }
    }

    /** Upstream {@code MCH_Radar#updateXZ}. */
    public void scan(Entity center, int range) {
        Level level = center.level();
        if (!level.isClientSide()) {
            return;
        }
        DoubleList entityList = new DoubleList();
        DoubleList enemyList = new DoubleList();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Entity entity : level.getEntities(center, center.getBoundingBox().inflate(range))) {
            if (!(entity instanceof Mob)) {
                continue;
            }
            double x = entity.getX() - center.getX();
            double z = entity.getZ() - center.getZ();
            if (x * x + z * z >= range * range) {
                continue;
            }
            int y = 1 + Mth.floor(entity.getY());
            if (y < level.getMinY()) {
                y = level.getMinY() + 1;
            }
            int blockCnt = 0;
            for (; y < COVER_SCAN_TOP; y++) {
                if (!level.getBlockState(pos.set(entity.getBlockX(), y, entity.getBlockZ())).isAir()) {
                    if (++blockCnt >= 5) {
                        break;
                    }
                }
            }
            if (blockCnt < 5) {
                (entity instanceof Monster ? enemyList : entityList).add(x, z);
            }
        }
        this.entities = entityList.toArray();
        this.enemies = enemyList.toArray();
    }

    private static final class DoubleList {
        private double[] data = new double[16];
        private int size;

        void add(double x, double z) {
            if (this.size + 2 > this.data.length) {
                this.data = java.util.Arrays.copyOf(this.data, this.data.length * 2);
            }
            this.data[this.size++] = x;
            this.data[this.size++] = z;
        }

        double[] toArray() {
            return java.util.Arrays.copyOf(this.data, this.size);
        }
    }
}
