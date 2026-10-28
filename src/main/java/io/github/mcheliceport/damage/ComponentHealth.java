package io.github.mcheliceport.damage;

/**
 * Up to eight components' health packed in one long (eight bits each, 255 = intact), the form the
 * vehicle syncs and saves. Plain values so the tests need no world.
 */
public final class ComponentHealth {

    public static final int SLOTS = 8;
    public static final int MAX_LEVEL = 255;
    /** Every slot intact. */
    public static final long FULL = -1L;

    private ComponentHealth() {
    }

    public static int level(long packed, int slot) {
        return (int) (packed >>> (slot * 8)) & 0xFF;
    }

    public static long withLevel(long packed, int slot, int level) {
        int clamped = Math.clamp(level, 0, MAX_LEVEL);
        long mask = 0xFFL << (slot * 8);
        return (packed & ~mask) | ((long) clamped << (slot * 8));
    }

    public static float health(long packed, int slot) {
        return level(packed, slot) / (float) MAX_LEVEL;
    }

    /**
     * Takes {@code fraction} of a component's whole health from a slot; any real hit costs at least
     * one level so chip damage adds up.
     */
    public static long damage(long packed, int slot, float fraction) {
        if (!(fraction > 0.0F)) {
            return packed;
        }
        int loss = Math.max(1, Math.round(fraction * MAX_LEVEL));
        return withLevel(packed, slot, level(packed, slot) - loss);
    }

    /**
     * Restores {@code fraction} of one component's health across the first {@code count} slots,
     * the most damaged first, spilling what is left over to the next.
     */
    public static long repair(long packed, int count, float fraction) {
        int budget = Math.round(fraction * MAX_LEVEL);
        while (budget > 0) {
            int worst = -1;
            for (int i = 0; i < count; i++) {
                if (level(packed, i) < MAX_LEVEL && (worst < 0 || level(packed, i) < level(packed, worst))) {
                    worst = i;
                }
            }
            if (worst < 0) {
                break;
            }
            int gain = Math.min(budget, MAX_LEVEL - level(packed, worst));
            packed = withLevel(packed, worst, level(packed, worst) + gain);
            budget -= gain;
        }
        return packed;
    }

    /**
     * Restores {@code hp} hull HP across the components whose whole health is {@code capacity[i]}
     * HP, the most damaged first, spilling what is left over to the next. With equal capacities it
     * matches {@link #repair}.
     */
    public static long repairHp(long packed, float[] capacity, float hp) {
        float budget = hp;
        while (budget > 0.0F) {
            int worst = -1;
            for (int i = 0; i < capacity.length; i++) {
                if (level(packed, i) < MAX_LEVEL && (worst < 0 || level(packed, i) < level(packed, worst))) {
                    worst = i;
                }
            }
            if (worst < 0) {
                break;
            }
            int missing = MAX_LEVEL - level(packed, worst);
            float perLevel = capacity[worst] / MAX_LEVEL;
            int gain = Math.min(missing, Math.round(budget / perLevel));
            if (gain <= 0) {
                break;
            }
            packed = withLevel(packed, worst, level(packed, worst) + gain);
            budget -= gain * perLevel;
        }
        return packed;
    }
}
