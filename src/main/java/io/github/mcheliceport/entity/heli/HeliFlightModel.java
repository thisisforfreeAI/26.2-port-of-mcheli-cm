package io.github.mcheliceport.entity.heli;

import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.entity.aircraft.AircraftAttitude;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Upstream's helicopter formulas ({@code MCH_EntityHeli}) as functions over a {@link Body}, so the
 * entity and the tests run the same code.
 *
 * <p>Every literal keeps upstream's type ({@code 0.02F} stays a float, {@code 0.02} a double),
 * because Java widens floats exactly but the products differ in the last bits otherwise. Where
 * upstream asks {@code world.isRemote}, these functions take {@code simulates}: true on the side
 * that moves the helicopter (the server upstream; the pilot's client in the port's default
 * authority mode, decision D2).
 */
public final class HeliFlightModel {

    /** Upstream's {@code currentSpeed} floor and start value. */
    public static final double MIN_CURRENT_SPEED = 0.07;

    private HeliFlightModel() {
    }

    /** The helicopter being simulated. */
    public interface Body extends AircraftAttitude.Rotatable {
        double motionX();

        double motionY();

        double motionZ();

        void setMotion(double x, double y, double z);

        double currentThrottle();

        void setCurrentThrottle(double throttle);

        double currentSpeed();

        void setCurrentSpeed(double speed);

        boolean onGround();

        boolean inWater();

        /** Upstream {@code getWaterDepth}: the submerged fraction of the box, 0 to 1. */
        double waterDepth();

        /** Upstream {@code MCH_Lib.getBlockIdY(this, size, lenY) > 0}, through {@code GroundProbe}. */
        boolean blockWithin(int size, int lenY);

        /** Moves by the given amount with collisions ({@code move(MoverType.SELF, ...)}). */
        void move(double x, double y, double z);

        RandomSource random();
    }

    /** Pilot's switches ({@code throttleUp}, {@code throttleDown}, {@code moveLeft}, {@code moveRight}). */
    public record Input(boolean throttleUp, boolean throttleDown, boolean moveLeft, boolean moveRight) {
        public static final Input NONE = new Input(false, false, false, false);
    }

    /**
     * The content values the formulas read. {@code speed} is already multiplied by
     * {@code AllHeliSpeed}: upstream applied that factor once at content load as
     * {@code (float) (speed * AllHeliSpeed)}; the port applies it here so a config change needs no
     * content reload.
     */
    public record Params(float speed, float gravity, float gravityInWater, float onGroundPitch,
            float throttleUpDown, float rotorSpeed, boolean enableFoldBlade) {

        public static Params of(HeliInfo info, double allHeliSpeed) {
            return new Params((float) (info.speed * allHeliSpeed), info.gravity, info.gravityInWater,
                    info.onGroundPitch, info.throttleUpDown, info.rotorSpeed, info.isEnableFoldBlade);
        }
    }

    /** Server rotation limits ({@code MCH_ServerSettings}); {@code NONE} when disabled. */
    public record RotationLimits(boolean enabled, float pitchMax, float pitchMin, float rollLimit) {
        public static final RotationLimits NONE = new RotationLimits(false, 10.0F, -10.0F, 35.0F);
    }

    // ---- onUpdate_Control ----

    /** Upstream {@code onUpdate_ControlNotHovering}. {@code autoThrottle} is the whole auto-down condition. */
    public static void controlNotHovering(Body b, Params p, Input in, boolean autoThrottle, boolean simulates) {
        float throttleUpDown = p.throttleUpDown();
        if (in.throttleUp()) {
            if (b.currentThrottle() < 1.0) {
                b.setCurrentThrottle(b.currentThrottle() + 0.02 * throttleUpDown);
            } else {
                b.setCurrentThrottle(1.0);
            }
        } else if (in.throttleDown()) {
            if (b.currentThrottle() > 0.0) {
                b.setCurrentThrottle(b.currentThrottle() + -0.014285714285714285 * throttleUpDown);
            } else {
                b.setCurrentThrottle(0.0);
            }
        } else if (autoThrottle) {
            if (b.currentThrottle() > 0.52) {
                b.setCurrentThrottle(b.currentThrottle() + -0.01 * throttleUpDown);
            } else if (b.currentThrottle() < 0.48) {
                b.setCurrentThrottle(b.currentThrottle() + 0.01 * throttleUpDown);
            }
        }

        if (simulates) {
            boolean move = false;
            float yaw;
            double x = 0.0;
            double z = 0.0;
            if (in.moveLeft() && !in.moveRight()) {
                yaw = b.getYaw() - 90.0F;
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }
            if (in.moveRight() && !in.moveLeft()) {
                yaw = b.getYaw() + 90.0F;
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }
            if (move) {
                double f = 1.0;
                double d = Math.sqrt(x * x + z * z);
                b.setMotion(b.motionX() - x / d * 0.02F * f * p.speed(), b.motionY(),
                        b.motionZ() + z / d * 0.02F * f * p.speed());
            }
        }
    }

    /** Upstream {@code onUpdate_ControlHovering}. */
    public static void controlHovering(Body b, Params p, Input in, boolean simulates) {
        if (b.currentThrottle() < 1.0) {
            b.setCurrentThrottle(b.currentThrottle() + 0.03333333333333333);
        } else {
            b.setCurrentThrottle(1.0);
        }

        if (simulates) {
            boolean move = false;
            float yaw;
            double x = 0.0;
            double z = 0.0;
            if (in.throttleUp()) {
                yaw = b.getYaw();
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }
            if (in.throttleDown()) {
                yaw = b.getYaw() - 180.0F;
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }
            if (in.moveLeft() && !in.moveRight()) {
                yaw = b.getYaw() - 90.0F;
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }
            if (in.moveRight() && !in.moveLeft()) {
                yaw = b.getYaw() + 90.0F;
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }
            if (move) {
                double d = Math.sqrt(x * x + z * z);
                b.setMotion(b.motionX() - x / d * 0.01F * p.speed(), b.motionY(),
                        b.motionZ() + z / d * 0.01F * p.speed());
            }
        }
    }

    /** Upstream {@code onUpdate_ControlFoldBladeAndOnGround}: taxiing with folded blades. */
    public static void controlFoldBladeOnGround(Body b, Input in, boolean simulates) {
        if (simulates) {
            boolean move = false;
            float yaw;
            double x = 0.0;
            double z = 0.0;
            if (in.throttleUp()) {
                yaw = b.getYaw();
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }
            if (in.throttleDown()) {
                yaw = b.getYaw() - 180.0F;
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }
            if (move) {
                double d = Math.sqrt(x * x + z * z);
                b.setMotion(b.motionX() - x / d * 0.03F, b.motionY(), b.motionZ() + z / d * 0.03F);
            }
        }
    }

    /** The idle branch of {@code onUpdate_Control}: throttle runs down by 0.00125 per tick. */
    public static void idleThrottle(Body b) {
        if (b.currentThrottle() > 0.0) {
            b.setCurrentThrottle(b.currentThrottle() + -0.00125);
        } else {
            b.setCurrentThrottle(0.0);
        }
    }

    /** Observers' clients chase the synced throttle by 0.01 per tick ({@code onUpdate_Control}). */
    public static void chaseSyncedThrottle(Body b, double synced) {
        if (b.currentThrottle() >= synced - 0.02) {
            b.setCurrentThrottle(b.currentThrottle() + -0.01);
        } else if (b.currentThrottle() < synced) {
            b.setCurrentThrottle(b.currentThrottle() + 0.01);
        }
    }

    /** The end of {@code onUpdate_Control}: the rotor angle after one tick, wrapped to (-360, 360). */
    public static double spinRotor(double rotation, double currentThrottle, float rotorSpeed) {
        rotation = rotation + (1.0 - Math.pow(1.0 - currentThrottle, 5.0)) * rotorSpeed;
        return rotation % 360.0;
    }

    // ---- onUpdate_Server ----

    /**
     * Upstream {@code onUpdate_Server} up to and including the move and damping (block breaking
     * and unmounting stay in the entity).
     */
    public static void physics(Body b, Params p, boolean hovering, boolean destroyed, boolean canFloatWater) {
        double motionX = b.motionX();
        double motionY = b.motionY();
        double motionZ = b.motionZ();
        double prevMotion = Math.sqrt(motionX * motionX + motionZ * motionZ);
        float ogp = p.onGroundPitch();
        if (!hovering) {
            double dp = 0.0;
            if (canFloatWater) {
                dp = b.waterDepth();
            }

            if (dp == 0.0) {
                motionY = motionY + (!b.inWater() ? p.gravity() : p.gravityInWater());
                float yaw = b.getYaw() / 180.0F * (float) Math.PI;
                float pitch = b.getPitch();
                if (b.blockWithin(3, -3)) {
                    pitch -= ogp;
                }

                motionX = motionX + 0.1 * Mth.sin(yaw) * b.currentSpeed()
                        * -(pitch * pitch * pitch / 20000.0F) * b.currentThrottle();
                motionZ = motionZ + 0.1 * Mth.cos(yaw) * b.currentSpeed()
                        * (pitch * pitch * pitch / 20000.0F) * b.currentThrottle();
                double y = 0.0;
                if (Mth.abs(b.getPitch()) + Mth.abs(b.getRoll() * 0.9F) <= 40.0F) {
                    y = 1.0 - y / 40.0;
                }

                double throttle = b.currentThrottle();
                if (destroyed) {
                    throttle *= -0.65;
                }

                motionY += (y * 0.025 + 0.03) * throttle;
            } else {
                if (Mth.abs(b.getPitch()) < 40.0F) {
                    float pitchx = b.getPitch();
                    pitchx -= ogp;
                    pitchx *= 0.9F;
                    pitchx += ogp;
                    b.setPitch(pitchx);
                }

                if (Mth.abs(b.getRoll()) < 40.0F) {
                    b.setRoll(b.getRoll() * 0.9F);
                }

                if (dp < 1.0) {
                    motionY -= 1.0E-4;
                    motionY = motionY + 0.007 * b.currentThrottle();
                } else {
                    if (motionY < 0.0) {
                        motionY *= 0.7;
                    }

                    motionY += 0.007;
                }
            }
        } else {
            RandomSource rand = b.random();
            if (rand.nextInt(50) == 0) {
                motionX = motionX + (rand.nextDouble() - 0.5) / 30.0;
            }

            if (rand.nextInt(50) == 0) {
                motionY = motionY + (rand.nextDouble() - 0.5) / 50.0;
            }

            if (rand.nextInt(50) == 0) {
                motionZ = motionZ + (rand.nextDouble() - 0.5) / 30.0;
            }
        }

        double motion = Math.sqrt(motionX * motionX + motionZ * motionZ);
        float speedLimit = p.speed();
        if (motion > speedLimit) {
            motionX *= speedLimit / motion;
            motionZ *= speedLimit / motion;
            motion = speedLimit;
        }

        double currentSpeed = b.currentSpeed();
        if (destroyed) {
            motionX *= 0.0;
            motionZ *= 0.0;
            currentSpeed = 0.0;
        }

        if (motion > prevMotion && currentSpeed < speedLimit) {
            currentSpeed = currentSpeed + (speedLimit - currentSpeed) / 35.0;
            if (currentSpeed > speedLimit) {
                currentSpeed = speedLimit;
            }
        } else {
            currentSpeed = currentSpeed - (currentSpeed - MIN_CURRENT_SPEED) / 35.0;
            if (currentSpeed < MIN_CURRENT_SPEED) {
                currentSpeed = MIN_CURRENT_SPEED;
            }
        }
        b.setCurrentSpeed(currentSpeed);

        if (b.onGround()) {
            motionX *= 0.5;
            motionZ *= 0.5;
            if (Mth.abs(b.getPitch()) < 40.0F) {
                float pitchx = b.getPitch();
                pitchx -= ogp;
                pitchx *= 0.9F;
                pitchx += ogp;
                b.setPitch(pitchx);
            }

            if (Mth.abs(b.getRoll()) < 40.0F) {
                b.setRoll(b.getRoll() * 0.9F);
            }
        }

        b.setMotion(motionX, motionY, motionZ);
        b.move(motionX, motionY, motionZ);
        b.setMotion(b.motionX() * 0.99, b.motionY() * 0.95, b.motionZ() * 0.99);
    }

    /** Upstream {@code applyOnGroundPitch}. */
    public static void applyOnGroundPitch(AircraftAttitude.Rotatable b, float onGroundPitch, float factor) {
        float pitch = b.getPitch();
        pitch -= onGroundPitch;
        pitch *= factor;
        pitch += onGroundPitch;
        b.setPitch(pitch);
        b.setRoll(b.getRoll() * factor);
    }

    /**
     * The movement half of upstream's {@code onUpdate_Client} for a client that does not simulate:
     * ground and water friction on the predicted motion.
     */
    public static void clientFriction(Body b, Params p, boolean destroyed) {
        if (!destroyed && (b.onGround() || b.blockWithin(1, -2))) {
            b.setMotion(b.motionX() * 0.95, b.motionY(), b.motionZ() * 0.95);
            applyOnGroundPitch(b, p.onGroundPitch(), 0.95F);
        }

        if (b.inWater()) {
            b.setMotion(b.motionX() * 0.99, b.motionY(), b.motionZ() * 0.99);
        }
    }

    /** The hovering pitch damping in upstream's {@code onUpdateAircraft}. */
    public static void hoverPitchDamping(AircraftAttitude.Rotatable b, boolean destroyed, boolean hovering) {
        if (!destroyed && hovering && Mth.abs(b.getPitch()) < 70.0F) {
            b.setPitch(b.getPitch() * 0.95F);
        }
    }

    // ---- onUpdateAngles ----

    /** Upstream {@code MCH_EntityHeli#onUpdateAngles}. */
    public static void onUpdateAngles(Body b, Params p, float deltaSeconds, boolean destroyed, boolean hovering,
            RotationLimits limits, Input in, boolean foldedBlades) {
        if (destroyed) {
            return;
        }
        float rotRoll = !hovering ? 0.04F : 0.07F;
        rotRoll = (float) Math.pow(1.0F - rotRoll, deltaSeconds * 20.0F);
        if (limits.enabled()) {
            if (b.getPitch() > limits.pitchMax()) {
                b.setPitch(b.getPitch() - Math.abs((b.getPitch() - limits.pitchMax()) * 2.0F * deltaSeconds));
            }

            if (b.getPitch() < limits.pitchMin()) {
                b.setPitch(b.getPitch() + Math.abs((b.getPitch() - limits.pitchMin()) * 4.0F * deltaSeconds));
            }

            if (b.getRoll() > limits.rollLimit()) {
                b.setRoll(b.getRoll() - Math.abs((b.getRoll() - limits.rollLimit()) * 0.6F * deltaSeconds));
            }

            if (b.getRoll() < -limits.rollLimit()) {
                b.setRoll(b.getRoll() + Math.abs((b.getRoll() + limits.rollLimit()) * 0.6F * deltaSeconds));
            }
        }

        if (b.getRoll() > 0.1 && b.getRoll() < 65.0F) {
            b.setRoll(b.getRoll() * rotRoll);
        }

        if (b.getRoll() < -0.1 && b.getRoll() > -65.0F) {
            b.setRoll(b.getRoll() * rotRoll);
        }

        if (!b.blockWithin(3, -3)) {
            if (in.moveLeft() && !in.moveRight()) {
                b.setRoll(b.getRoll() - 24.0F * deltaSeconds);
            }

            if (in.moveRight() && !in.moveLeft()) {
                b.setRoll(b.getRoll() + 24.0F * deltaSeconds);
            }
        } else {
            if (Mth.abs(b.getPitch()) < 40.0F) {
                applyOnGroundPitch(b, p.onGroundPitch(), 0.97F);
            }

            if (p.enableFoldBlade() && foldedBlades) {
                if (in.moveLeft() && !in.moveRight()) {
                    b.setYaw(b.getYaw() - 10.0F * deltaSeconds);
                }

                if (in.moveRight() && !in.moveLeft()) {
                    b.setYaw(b.getYaw() + 10.0F * deltaSeconds);
                }
            }
        }
    }
}
