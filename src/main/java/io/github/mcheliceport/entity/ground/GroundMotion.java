package io.github.mcheliceport.entity.ground;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.TankInfo;
import io.github.mcheliceport.content.aircraft.VehicleInfo;
import io.github.mcheliceport.entity.plane.PlaneFlightModel.Body;
import net.minecraft.util.Mth;

/** Tick formulas from MCH_EntityTank and MCH_EntityVehicle at the pinned upstream baseline. */
public final class GroundMotion {
    private GroundMotion() { }

    public static void grip(Body b, double lateral, double rolling) {
        double x = -Math.sin(Math.toRadians(b.getYaw())), z = Math.cos(Math.toRadians(b.getYaw()));
        double forward = (b.motionX() * x + b.motionZ() * z) * rolling;
        double side = (-b.motionX() * z + b.motionZ() * x) * lateral;
        b.setMotion(x * forward - z * side, b.motionY(), z * forward + x * side);
    }

    public static void tankControl(Body b, TankInfo info, boolean up, boolean down,
            boolean brake, boolean active, boolean autoDown) {
        b.setThrottleBack((float) (b.throttleBack() * 0.8));
        double throttle = b.currentThrottle();
        float back = b.throttleBack();
        if (brake) {
            back *= 0.5F;
            throttle = Math.max(0, throttle - 0.02 * info.throttleUpDown);
        }
        if (active) {
            if (up) {
                if (info.enableBack && back > 0) { back -= 0.01 * info.throttleUpDown; }
                else { back = 0; throttle = throttle < 1 ? throttle + 0.01 * info.throttleUpDown : 1; }
            } else if (down) {
                if (throttle > 0) { throttle -= 0.01 * info.throttleUpDown; }
                else { throttle = 0; if (info.enableBack) { back += 0.0025 * info.throttleUpDown * info.throttleDownFactor; } }
            } else if (autoDown) { throttle -= 0.005 * info.throttleUpDown; }
        } else { throttle -= 0.0025 * info.throttleUpDown; }
        b.setThrottleBack(Math.max(0, back));
        b.setCurrentThrottle(Math.max(0, throttle));
    }

    public static void tankPhysics(Body b, TankInfo info, boolean canFloat, Runnable wheels) {
        double previous = Math.hypot(b.motionX(), b.motionZ());
        double depth = canFloat ? b.waterDepth() : 0;
        double y = b.motionY();
        if (depth == 0) {
            y += 0.04 + (b.inWater() ? info.gravityInWater : info.gravity);
            y += -0.047 * (1 - b.currentThrottle());
        } else if (Math.abs(b.getRoll()) < 40 && depth >= 1) {
            if (y < 0) { y /= 2; }
            y += 0.007;
        } else { y += -1.0E-4 + 0.007 * b.currentThrottle(); }
        boolean ground = b.onGround() || b.blockWithin(1, -2);
        double yaw = Math.toRadians(b.getYaw());
        double pitch = ground ? 0 : Math.toRadians(b.getPitch() - 10);
        double x = -Math.sin(yaw) * Math.cos(pitch), z = Math.cos(yaw) * Math.cos(pitch);
        double vy = -Math.sin(pitch);
        float throttle = (float) (b.currentThrottle() / 10);
        if (!b.onGround() && previous > 0.05) { y += vy * throttle / 8; }
        double power = info.enableBack && b.throttleBack() > 0 ? -b.throttleBack() : throttle;
        boolean canMove = info.canMoveOnGround || !b.groundIsSolid();
        double mx = b.motionX() + (canMove ? x * power : 0), mz = b.motionZ() + (canMove ? z * power : 0);
        double speed = Math.hypot(mx, mz);
        if (speed > info.speed && speed > 0) { mx *= info.speed / speed; mz *= info.speed / speed; speed = info.speed; }
        double target = speed > previous && b.currentSpeed() < info.speed ? info.speed : 0.07;
        b.setCurrentSpeed(target == info.speed ? Math.min(info.speed, b.currentSpeed() + (target - b.currentSpeed()) / 35)
                : Math.max(0.07, b.currentSpeed() - (b.currentSpeed() - 0.07) / 35));
        b.setMotion(mx, y, mz);
        if (ground) {
            grip(b, info.weightType == 1 ? 0.18 : 0.35, info.weightType == 1 ? 0.985 : info.motionFactor);
            if (Math.abs(b.motionY()) < 0.03) { b.setMotion(b.motionX(), 0, b.motionZ()); }
        }
        wheels.run();
        b.move(b.motionX(), b.motionY(), b.motionZ());
        b.setMotion(b.motionX(), b.motionY() * 0.95, b.motionZ());
        if (ground) { grip(b, info.weightType == 1 ? 0.10 : 0.20, info.weightType == 1 ? 0.992 : info.motionFactor); }
        else { b.setMotion(b.motionX() * info.motionFactor, b.motionY(), b.motionZ() * info.motionFactor); }
    }

    public static float vehicleSteer(float current, boolean left, boolean right) {
        float target = left == right ? 0 : left ? -1 : 1;
        float rate = target == 0 ? 0.20F : current * target < 0 ? 0.30F : 0.14F;
        return current + Mth.clamp(target - current, -rate, rate);
    }

    public static void vehiclePhysics(Body b, VehicleInfo info, boolean up, boolean down,
            float steer, boolean active, boolean canFloat) {
        double yaw = Math.toRadians(b.getYaw());
        double x = -Math.sin(yaw), z = Math.cos(yaw);
        double mx = b.motionX(), mz = b.motionZ();
        double signed = mx * x + mz * z;
        if (active && info.isEnableRot && Math.abs(steer) > 1.0E-4 && Math.abs(signed) > 1.0E-4) {
            b.setYaw(b.getYaw() + (float) Math.toDegrees(signed / 3 * Math.tan(Math.toRadians(steer * 34))));
        }
        if (active && info.isEnableMove && up != down) {
            double accel = 0.035F * (up ? 1 : -1);
            mx += x * accel; mz += z * accel;
        }
        double depth = canFloat ? b.waterDepth() : 0;
        double y = b.motionY();
        if (depth == 0) { y += canFloat && b.inWater() ? info.gravityInWater : info.gravity; }
        else if (depth < 1) { y += -1.0E-4 + 0.007 * b.currentThrottle(); }
        else { if (y < 0) { y *= 0.5; } y += 0.007; }
        double power = 0.035 * b.currentThrottle();
        yaw = Math.toRadians(b.getYaw());
        mx -= Math.sin(yaw) * power; mz += Math.cos(yaw) * power;
        double h = Math.hypot(mx, mz);
        if (h > info.speed && h > 0) { mx *= info.speed / h; mz *= info.speed / h; }
        b.setMotion(mx, y * 0.95, mz);
        if (b.onGround()) { grip(b, 0.08, 0.985); }
        else { b.setMotion(b.motionX() * 0.99, b.motionY(), b.motionZ() * 0.99); }
        b.move(b.motionX(), b.motionY(), b.motionZ());
    }

    public static float terrainAngle(float current, float target, float seconds, boolean ground,
            double speed, boolean roll) {
        float interp = ground ? seconds * (speed > 0.08 ? roll ? 9 : 10 : roll ? 6 : 7)
                : seconds * (roll ? 3.5F : 4);
        float limit = (ground ? 90 : roll ? 20 : 24) * seconds;
        return current + Mth.clamp((target - current) * Math.min(1, interp), -limit, limit);
    }
}
