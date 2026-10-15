package io.github.mcheliceport.entity.plane;

import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.entity.aircraft.AircraftAttitude;
import net.minecraft.util.Mth;

/** Movement formulas from McHeli CE 1.5.1's MCH_EntityPlane, separated from world/network state. */
public final class PlaneFlightModel {
    private PlaneFlightModel() {
    }

    public interface Body extends AircraftAttitude.Rotatable {
        double motionX();
        double motionY();
        double motionZ();
        void setMotion(double x, double y, double z);
        double currentThrottle();
        void setCurrentThrottle(double throttle);
        float throttleBack();
        void setThrottleBack(float throttle);
        double currentSpeed();
        void setCurrentSpeed(double speed);
        boolean onGround();
        boolean inWater();
        double waterDepth();
        boolean blockWithin(int size, int depth);
        boolean groundIsSolid();
        void move(double x, double y, double z);
    }

    public record Input(boolean up, boolean down, boolean left, boolean right) {
        public static final Input NONE = new Input(false, false, false, false);
    }

    public record Params(float speed, float sweepSpeed, boolean variableSweep, float gravity,
            float gravityInWater, float motionFactor, float onGroundPitch, float throttleUpDown,
            boolean enableBack, float pivotTurnThrottle, float throttleUpDownOnEntity,
            boolean canMoveOnGround, float autoPilotRot) {
        public static Params of(PlaneInfo info, double speedMultiplier) {
            return new Params((float) (info.speed * speedMultiplier),
                    (float) (info.sweepWingSpeed * speedMultiplier), info.isVariableSweepWing,
                    info.gravity, info.gravityInWater, info.motionFactor, info.onGroundPitch,
                    info.throttleUpDown, info.enableBack, info.pivotTurnThrottle,
                    info.throttleUpDownOnEntity, info.canMoveOnGround, info.autoPilotRot);
        }

        public float speedLimit(float wingFactor) {
            return this.speed + (this.variableSweep ? (this.sweepSpeed - this.speed) * wingFactor : 0.0F);
        }
    }

    /** Upstream onUpdate_ControlNotHovering. Carrier speed is negative when not riding a carrier. */
    public static void control(Body b, Params p, Input in, boolean gunner, boolean autoThrottle,
            double carrierSpeed) {
        if (gunner) {
            return;
        }
        float rate = p.throttleUpDown;
        boolean up = in.up;
        if (in.left != in.right && b.currentThrottle() < p.pivotTurnThrottle && !up && !in.down) {
            up = true;
            rate *= 2.0F;
        }
        if (up) {
            float f = carrierSpeed >= 0.0 ? rate * ((float) carrierSpeed * p.throttleUpDownOnEntity) : rate;
            if (p.enableBack && b.throttleBack() > 0.0F) {
                b.setThrottleBack((float) (b.throttleBack() - 0.01 * f));
            } else {
                b.setThrottleBack(0.0F);
                b.setCurrentThrottle(b.currentThrottle() < 1.0 ? b.currentThrottle() + 0.01 * f : 1.0);
            }
        } else if (in.down) {
            if (b.currentThrottle() > 0.0) {
                b.setCurrentThrottle(b.currentThrottle() - 0.01 * rate);
            } else {
                b.setCurrentThrottle(0.0);
                if (p.enableBack) {
                    b.setThrottleBack(Math.min(0.6F, (float) (b.throttleBack() + 0.0025 * rate)));
                }
            }
        } else if (autoThrottle && b.currentThrottle() > 0.0) {
            b.setCurrentThrottle(Math.max(0.0, b.currentThrottle() - 0.005 * rate));
        }
    }

    public static void idle(Body b, Params p) {
        b.setCurrentThrottle(Math.max(0.0, b.currentThrottle() - 0.0025 * p.throttleUpDown));
    }

    public static void groundPitch(Body b, Params p, float factor) {
        b.setPitch((b.getPitch() - p.onGroundPitch) * factor + p.onGroundPitch);
    }

    /**
     * One upstream onUpdate_Server step, invoked once per entity tick.
     * Collision response may change velocity in move().
     * Returns true when a target drone cruising above terrain requests folded landing gear.
     */
    public static boolean physics(Body b, Params p, float nozzle, float wingFactor, boolean gunner,
            boolean floatWater, boolean activeDrone) {
        double prevMotion = Math.sqrt(b.motionX() * b.motionX() + b.motionZ() * b.motionZ());
        double depth = floatWater ? b.waterDepth() : 0.0;
        boolean levelOff = gunner;
        boolean foldGear = false;
        if (depth == 0.0) {
            if (activeDrone) {
                if (b.blockWithin(3, -40)) {
                    if (!b.blockWithin(3, -5)) {
                        b.setYaw(b.getYaw() + p.autoPilotRot * 2.0F);
                        if (b.getPitch() > -20.0F) {
                            b.setPitch(b.getPitch() - 0.5F);
                        }
                    }
                } else {
                    b.setYaw(b.getYaw() + p.autoPilotRot);
                    b.setPitch(b.getPitch() * 0.95F);
                    foldGear = true;
                    levelOff = true;
                }
            }
            if (!levelOff) {
                double y = b.motionY() + (0.04 + (b.inWater() ? p.gravityInWater : p.gravity));
                b.setMotion(b.motionX(), y + -0.047 * (1.0 - b.currentThrottle()), b.motionZ());
            } else {
                b.setMotion(b.motionX(), b.motionY() * 0.8, b.motionZ());
            }
        } else {
            b.setPitch(b.getPitch() * 0.8F);
            if (Math.abs(b.getRoll()) < 40.0F) {
                b.setRoll(b.getRoll() * 0.9F);
            }
            double y = b.motionY();
            if (depth < 1.0) {
                y -= 1.0E-4;
                y = y + 0.007 * b.currentThrottle();
            } else {
                if (y < 0.0) {
                    y /= 2.0;
                }
                y += 0.007;
            }
            b.setMotion(b.motionX(), y, b.motionZ());
        }
        float throttle = (float) (b.currentThrottle() / 10.0);
        float pitch;
        if (nozzle > 0.001F) {
            b.setPitch(b.getPitch() * 0.95F);
            pitch = b.getPitch() - nozzle;
        } else {
            pitch = b.getPitch() - 10.0F;
        }
        // Keep the float trigonometry and products of MCH_Lib.Rot2Vec3.
        float yr = b.getYaw() / 180.0F * (float) Math.PI;
        float pr = pitch / 180.0F * (float) Math.PI;
        double x = -Mth.sin(yr) * Mth.cos(pr);
        double y = -Mth.sin(pr);
        double z = Mth.cos(yr) * Mth.cos(pr);
        if (nozzle >= 90.0F) {
            x *= 0.8F;
            z *= 0.8F;
        }
        if (!levelOff) {
            b.setMotion(b.motionX(), b.motionY() + y * throttle / (nozzle <= 0.01F ? 2.0 : 8.0), b.motionZ());
        }
        if (p.canMoveOnGround || !b.groundIsSolid()) {
            if (p.enableBack && b.throttleBack() > 0.0F) {
                b.setMotion(b.motionX() - x * b.throttleBack(), b.motionY(), b.motionZ() - z * b.throttleBack());
            } else {
                b.setMotion(b.motionX() + x * throttle, b.motionY(), b.motionZ() + z * throttle);
            }
        }
        double motion = Math.sqrt(b.motionX() * b.motionX() + b.motionZ() * b.motionZ());
        float limit = p.speedLimit(wingFactor);
        if (motion > limit) {
            b.setMotion(b.motionX() * (limit / motion), b.motionY(), b.motionZ() * (limit / motion));
            motion = limit;
        }
        if (motion > prevMotion && b.currentSpeed() < limit) {
            b.setCurrentSpeed(Math.min(limit, b.currentSpeed() + (limit - b.currentSpeed()) / 35.0));
        } else {
            b.setCurrentSpeed(Math.max(0.07, b.currentSpeed() - (b.currentSpeed() - 0.07) / 35.0));
        }
        if (b.onGround() || b.blockWithin(1, -2)) {
            b.setMotion(b.motionX() * p.motionFactor, b.motionY(), b.motionZ() * p.motionFactor);
            if (Math.abs(b.getPitch()) < 40.0F) {
                groundPitch(b, p, 0.8F);
            }
        }
        b.move(b.motionX(), b.motionY(), b.motionZ());
        b.setMotion(b.motionX() * p.motionFactor, b.motionY() * 0.95, b.motionZ() * p.motionFactor);
        return foldGear;
    }
}
