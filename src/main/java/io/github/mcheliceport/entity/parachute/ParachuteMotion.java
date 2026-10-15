package io.github.mcheliceport.entity.parachute;

import net.minecraft.world.phys.Vec3;

/** Server movement formulas from MCH_EntityParachute at cbaff7f, without world queries. */
public final class ParachuteMotion {
    private ParachuteMotion() {
    }

    public record Step(Vec3 velocity, double speedMultiplier) {
    }

    public static boolean isOpen(int type, double motionY) {
        return type != 2 || motionY < -0.1;
    }

    /** Gravity, forward steering, speed cap and the boat-style acceleration multiplier. */
    public static Step accelerate(int type, int age, boolean onGround, Vec3 motion, double multiplier,
            boolean forward, float riderYaw) {
        double oldSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        double gravity = onGround ? 0.01 : 0.03;
        if (type == 2 && age < 20) {
            gravity = 0.01;
        }
        double x = motion.x;
        double y = motion.y - gravity;
        double z = motion.z;
        if (isOpen(type, y)) {
            if (forward) {
                x += -Math.sin(riderYaw * (float) Math.PI / 180.0F) * multiplier * 0.05;
                z += Math.cos(riderYaw * (float) Math.PI / 180.0F) * multiplier * 0.05;
            }
            double speed = Math.sqrt(x * x + z * z);
            if (speed > 0.35) {
                x *= 0.35 / speed;
                z *= 0.35 / speed;
                speed = 0.35;
            }
            if (speed > oldSpeed && multiplier < 0.35) {
                multiplier = Math.min(0.35, multiplier + (0.35 - multiplier) / 35.0);
            } else {
                multiplier = Math.max(0.07, multiplier - (multiplier - 0.07) / 35.0);
            }
        }
        return new Step(new Vec3(x, y, z), multiplier);
    }

    /** Applied after Entity.move, using the velocity left by collisions, as upstream. */
    public static Vec3 drag(int type, int age, Vec3 motion) {
        double y = motion.y * (type == 2 && age < 20 ? 0.95 : 0.9);
        double horizontal = isOpen(type, y) ? 0.95 : 0.99;
        return new Vec3(motion.x * horizontal, y, motion.z * horizontal);
    }
}
