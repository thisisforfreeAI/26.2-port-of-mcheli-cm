package io.github.mcheliceport.entity.heli;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A line-for-line transcription of upstream McHeli CE 1.5.1's helicopter flight code, used as the
 * test oracle for {@link HeliFlightModel} (docs/M3_PLAN.md section 8.1). It is written straight
 * from {@code MCH_EntityHeli} ({@code onUpdate_Control}, {@code onUpdate_ControlNotHovering},
 * {@code onUpdate_ControlHovering}, {@code onUpdate_ControlFoldBladeAndOnGround},
 * {@code onUpdate_Server}, {@code onUpdateAngles}, the friction of {@code onUpdate_Client}) and
 * {@code MCH_EntityAircraft#applyOnGroundPitch}, with fields instead of the entity, and
 * {@code world.isRemote} as a flag. It shares no code with the port.
 *
 * <p>The world is the test's: {@link World} answers the ground probe and moves the body.
 */
final class UpstreamHeliOracle {

    /** The bits of the world the formulas look at. */
    interface World {
        /** {@code MCH_Lib.getBlockIdY(this, size, lenY) > 0}. */
        boolean blockIdY(UpstreamHeliOracle heli, int size, int lenY);

        /** {@code move(MoverType.SELF, x, y, z)}: moves the body and sets {@code onGround}. */
        void move(UpstreamHeliOracle heli, double x, double y, double z);
    }

    // MCH_HeliInfo
    float speed;
    float gravity;
    float gravityInWater;
    float onGroundPitch;
    float throttleUpDown;
    float rotorSpeed;
    boolean isEnableFoldBlade;
    int rotorCount = 1;

    // MCH_EntityAircraft / MCH_EntityHeli state
    double posX;
    double posY;
    double posZ;
    double motionX;
    double motionY;
    double motionZ;
    float rotationYaw;
    float rotationPitch;
    float rotationRoll;
    double currentThrottle;
    double syncedThrottle;
    double currentSpeed = 0.07;
    double rotationRotor;
    boolean onGround;
    boolean inWater;
    double waterDepth;
    boolean throttleUp;
    boolean throttleDown;
    boolean moveLeft;
    boolean moveRight;
    boolean hoveringMode;
    boolean isGunnerMode;
    boolean destroyed;
    boolean exploded;
    boolean riddenByEntity = true;
    boolean canUseFuel = true;
    boolean canUseBlades = true;
    boolean canopyClose = true;
    byte foldBladeStat = 2;
    boolean isFloat;
    boolean cs_heliAutoThrottleDown = true;
    boolean isRemote;
    boolean riddenByClientPlayer;

    // MCH_ServerSettings
    boolean enableRotationLimit;
    float pitchLimitMax = 10.0F;
    float pitchLimitMin = -10.0F;
    float rollLimit = 35.0F;

    /**
     * Upstream drew the hovering jitter from the entity's {@code java.util.Random}. 26.2 entities
     * carry a {@link RandomSource}, whose {@code nextDouble} rounds through a float constant and so
     * differs from {@code java.util.Random}'s in the last bits; the oracle uses the same kind of
     * source, so the comparison checks the formulas rather than the platform's random numbers.
     */
    final RandomSource rand;
    final World world;

    UpstreamHeliOracle(World world, long seed) {
        this.world = world;
        this.rand = RandomSource.create(seed);
    }

    float getYaw() {
        return this.rotationYaw;
    }

    float getPitch() {
        return this.rotationPitch;
    }

    float getRoll() {
        return this.rotationRoll;
    }

    void setRotYaw(float f) {
        this.rotationYaw = f;
    }

    void setRotPitch(float f) {
        this.rotationPitch = f;
    }

    void setRotRoll(float f) {
        this.rotationRoll = f;
    }

    double getCurrentThrottle() {
        return this.currentThrottle;
    }

    void setCurrentThrottle(double t) {
        this.currentThrottle = t;
    }

    void addCurrentThrottle(double t) {
        this.setCurrentThrottle(this.getCurrentThrottle() + t);
    }

    boolean isHovering() {
        return this.isGunnerMode || this.hoveringMode;
    }

    boolean canFloatWater() {
        return this.isFloat && !this.destroyed;
    }

    // ---- onUpdateAircraft, the part between the control and the movement ----

    void onUpdateAircraftMiddle() {
        if (!this.destroyed && this.isHovering() && Mth.abs(this.getPitch()) < 70.0F) {
            this.setRotPitch(this.getPitch() * 0.95F);
        }

        if (this.destroyed && this.getCurrentThrottle() > 0.0) {
            if (this.world.blockIdY(this, 3, -2)) {
                this.setCurrentThrottle(this.getCurrentThrottle() * 0.8);
            }

            if (this.exploded) {
                this.setCurrentThrottle(this.getCurrentThrottle() * 0.98);
            }
        }
    }

    // ---- onUpdate_Control (without the mode switches, which need fuel state the test sets) ----

    void onUpdate_Control() {
        if (!this.destroyed && (this.riddenByEntity || this.hoveringMode) && this.canUseBlades && this.canopyClose
                && this.canUseFuel) {
            if (!this.isHovering()) {
                this.onUpdate_ControlNotHovering();
            } else {
                this.onUpdate_ControlHovering();
            }
        } else {
            if (this.getCurrentThrottle() > 0.0) {
                this.addCurrentThrottle(-0.00125);
            } else {
                this.setCurrentThrottle(0.0);
            }

            if (this.isEnableFoldBlade && this.rotorCount > 0 && this.foldBladeStat == 0 && this.onGround
                    && !this.destroyed) {
                this.onUpdate_ControlFoldBladeAndOnGround();
            }
        }

        if (this.isRemote) {
            if (!this.riddenByClientPlayer) {
                double ct = this.syncedThrottle;
                if (this.getCurrentThrottle() >= ct - 0.02) {
                    this.addCurrentThrottle(-0.01);
                } else if (this.getCurrentThrottle() < ct) {
                    this.addCurrentThrottle(0.01);
                }
            }
        }

        if (this.getCurrentThrottle() < 0.0) {
            this.setCurrentThrottle(0.0);
        }

        this.rotationRotor = this.rotationRotor + (1.0 - Math.pow(1.0 - this.getCurrentThrottle(), 5.0)) * this.rotorSpeed;
        this.rotationRotor %= 360.0;
    }

    void onUpdate_ControlNotHovering() {
        float throttleUpDown = this.throttleUpDown;
        if (this.throttleUp) {
            if (this.getCurrentThrottle() < 1.0) {
                this.addCurrentThrottle(0.02 * throttleUpDown);
            } else {
                this.setCurrentThrottle(1.0);
            }
        } else if (this.throttleDown) {
            if (this.getCurrentThrottle() > 0.0) {
                this.addCurrentThrottle(-0.014285714285714285 * throttleUpDown);
            } else {
                this.setCurrentThrottle(0.0);
            }
        } else if ((!this.isRemote || this.riddenByClientPlayer) && this.cs_heliAutoThrottleDown) {
            if (this.getCurrentThrottle() > 0.52) {
                this.addCurrentThrottle(-0.01 * throttleUpDown);
            } else if (this.getCurrentThrottle() < 0.48) {
                this.addCurrentThrottle(0.01 * throttleUpDown);
            }
        }

        if (!this.isRemote) {
            boolean move = false;
            float yaw;
            double x = 0.0;
            double z = 0.0;
            if (this.moveLeft && !this.moveRight) {
                yaw = this.getYaw() - 90.0F;
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }

            if (this.moveRight && !this.moveLeft) {
                yaw = this.getYaw() + 90.0F;
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }

            if (move) {
                double f = 1.0;
                double d = Math.sqrt(x * x + z * z);
                this.motionX = this.motionX - x / d * 0.02F * f * this.speed;
                this.motionZ = this.motionZ + z / d * 0.02F * f * this.speed;
            }
        }
    }

    void onUpdate_ControlHovering() {
        if (this.getCurrentThrottle() < 1.0) {
            this.addCurrentThrottle(0.03333333333333333);
        } else {
            this.setCurrentThrottle(1.0);
        }

        if (!this.isRemote) {
            boolean move = false;
            float yaw;
            double x = 0.0;
            double z = 0.0;
            if (this.throttleUp) {
                yaw = this.getYaw();
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }

            if (this.throttleDown) {
                yaw = this.getYaw() - 180.0F;
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }

            if (this.moveLeft && !this.moveRight) {
                yaw = this.getYaw() - 90.0F;
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }

            if (this.moveRight && !this.moveLeft) {
                yaw = this.getYaw() + 90.0F;
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }

            if (move) {
                double d = Math.sqrt(x * x + z * z);
                this.motionX = this.motionX - x / d * 0.01F * this.speed;
                this.motionZ = this.motionZ + z / d * 0.01F * this.speed;
            }
        }
    }

    void onUpdate_ControlFoldBladeAndOnGround() {
        if (!this.isRemote) {
            boolean move = false;
            float yaw;
            double x = 0.0;
            double z = 0.0;
            if (this.throttleUp) {
                yaw = this.getYaw();
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }

            if (this.throttleDown) {
                yaw = this.getYaw() - 180.0F;
                x += Math.sin(yaw * Math.PI / 180.0);
                z += Math.cos(yaw * Math.PI / 180.0);
                move = true;
            }

            if (move) {
                double d = Math.sqrt(x * x + z * z);
                this.motionX -= x / d * 0.03F;
                this.motionZ += z / d * 0.03F;
            }
        }
    }

    // ---- onUpdate_Server, up to and including the move and damping ----

    void onUpdate_Server() {
        double prevMotion = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
        float ogp = this.onGroundPitch;
        if (!this.isHovering()) {
            double dp = 0.0;
            if (this.canFloatWater()) {
                dp = this.waterDepth;
            }

            if (dp == 0.0) {
                this.motionY = this.motionY + (!this.inWater ? this.gravity : this.gravityInWater);
                float yaw = this.getYaw() / 180.0F * (float) Math.PI;
                float pitch = this.getPitch();
                if (this.world.blockIdY(this, 3, -3)) {
                    pitch -= ogp;
                }

                this.motionX = this.motionX + 0.1 * Mth.sin(yaw) * this.currentSpeed
                        * -(pitch * pitch * pitch / 20000.0F) * this.getCurrentThrottle();
                this.motionZ = this.motionZ + 0.1 * Mth.cos(yaw) * this.currentSpeed
                        * (pitch * pitch * pitch / 20000.0F) * this.getCurrentThrottle();
                double y = 0.0;
                if (Mth.abs(this.getPitch()) + Mth.abs(this.getRoll() * 0.9F) <= 40.0F) {
                    y = 1.0 - y / 40.0;
                }

                double throttle = this.getCurrentThrottle();
                if (this.destroyed) {
                    throttle *= -0.65;
                }

                this.motionY += (y * 0.025 + 0.03) * throttle;
            } else {
                if (Mth.abs(this.getPitch()) < 40.0F) {
                    float pitchx = this.getPitch();
                    pitchx -= ogp;
                    pitchx *= 0.9F;
                    pitchx += ogp;
                    this.setRotPitch(pitchx);
                }

                if (Mth.abs(this.getRoll()) < 40.0F) {
                    this.setRotRoll(this.getRoll() * 0.9F);
                }

                if (dp < 1.0) {
                    this.motionY -= 1.0E-4;
                    this.motionY = this.motionY + 0.007 * this.getCurrentThrottle();
                } else {
                    if (this.motionY < 0.0) {
                        this.motionY *= 0.7;
                    }

                    this.motionY += 0.007;
                }
            }
        } else {
            if (this.rand.nextInt(50) == 0) {
                this.motionX = this.motionX + (this.rand.nextDouble() - 0.5) / 30.0;
            }

            if (this.rand.nextInt(50) == 0) {
                this.motionY = this.motionY + (this.rand.nextDouble() - 0.5) / 50.0;
            }

            if (this.rand.nextInt(50) == 0) {
                this.motionZ = this.motionZ + (this.rand.nextDouble() - 0.5) / 30.0;
            }
        }

        double motion = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
        float speedLimit = this.speed;
        if (motion > speedLimit) {
            this.motionX *= speedLimit / motion;
            this.motionZ *= speedLimit / motion;
            motion = speedLimit;
        }

        if (this.destroyed) {
            this.motionX *= 0.0;
            this.motionZ *= 0.0;
            this.currentSpeed = 0.0;
        }

        if (motion > prevMotion && this.currentSpeed < speedLimit) {
            this.currentSpeed = this.currentSpeed + (speedLimit - this.currentSpeed) / 35.0;
            if (this.currentSpeed > speedLimit) {
                this.currentSpeed = speedLimit;
            }
        } else {
            this.currentSpeed = this.currentSpeed - (this.currentSpeed - 0.07) / 35.0;
            if (this.currentSpeed < 0.07) {
                this.currentSpeed = 0.07;
            }
        }

        if (this.onGround) {
            this.motionX *= 0.5;
            this.motionZ *= 0.5;
            if (Mth.abs(this.getPitch()) < 40.0F) {
                float pitchx = this.getPitch();
                pitchx -= ogp;
                pitchx *= 0.9F;
                pitchx += ogp;
                this.setRotPitch(pitchx);
            }

            if (Mth.abs(this.getRoll()) < 40.0F) {
                this.setRotRoll(this.getRoll() * 0.9F);
            }
        }

        this.world.move(this, this.motionX, this.motionY, this.motionZ);
        this.motionY *= 0.95;
        this.motionX *= 0.99;
        this.motionZ *= 0.99;
    }

    // ---- onUpdate_Client's friction (no position update from the server this tick) ----

    void onUpdate_ClientFriction() {
        if (!this.destroyed && (this.onGround || this.world.blockIdY(this, 1, -2))) {
            this.motionX *= 0.95;
            this.motionZ *= 0.95;
            this.applyOnGroundPitch(0.95F);
        }

        if (this.inWater) {
            this.motionX *= 0.99;
            this.motionZ *= 0.99;
        }
    }

    void applyOnGroundPitch(float factor) {
        float ogp = this.onGroundPitch;
        float pitch = this.getPitch();
        pitch -= ogp;
        pitch *= factor;
        pitch += ogp;
        this.setRotPitch(pitch);
        this.setRotRoll(this.getRoll() * factor);
    }

    // ---- onUpdateAngles ----

    void onUpdateAngles(float deltaSeconds) {
        if (!this.destroyed) {
            float rotRoll = !this.isHovering() ? 0.04F : 0.07F;
            rotRoll = (float) Math.pow(1.0F - rotRoll, deltaSeconds * 20.0F);
            if (this.enableRotationLimit) {
                if (this.getPitch() > this.pitchLimitMax) {
                    this.setRotPitch(this.getPitch() - Math.abs((this.getPitch() - this.pitchLimitMax) * 2.0F * deltaSeconds));
                }

                if (this.getPitch() < this.pitchLimitMin) {
                    this.setRotPitch(this.getPitch() + Math.abs((this.getPitch() - this.pitchLimitMin) * 4.0F * deltaSeconds));
                }

                if (this.getRoll() > this.rollLimit) {
                    this.setRotRoll(this.getRoll() - Math.abs((this.getRoll() - this.rollLimit) * 0.6F * deltaSeconds));
                }

                if (this.getRoll() < -this.rollLimit) {
                    this.setRotRoll(this.getRoll() + Math.abs((this.getRoll() + this.rollLimit) * 0.6F * deltaSeconds));
                }
            }

            if (this.getRoll() > 0.1 && this.getRoll() < 65.0F) {
                this.setRotRoll(this.getRoll() * rotRoll);
            }

            if (this.getRoll() < -0.1 && this.getRoll() > -65.0F) {
                this.setRotRoll(this.getRoll() * rotRoll);
            }

            if (!this.world.blockIdY(this, 3, -3)) {
                if (this.moveLeft && !this.moveRight) {
                    this.setRotRoll(this.getRoll() - 24.0F * deltaSeconds);
                }

                if (this.moveRight && !this.moveLeft) {
                    this.setRotRoll(this.getRoll() + 24.0F * deltaSeconds);
                }
            } else {
                if (Mth.abs(this.getPitch()) < 40.0F) {
                    this.applyOnGroundPitch(0.97F);
                }

                if (this.isEnableFoldBlade && this.rotorCount > 0 && this.foldBladeStat == 0 && !this.destroyed) {
                    if (this.moveLeft && !this.moveRight) {
                        this.setRotYaw(this.getYaw() - 10.0F * deltaSeconds);
                    }

                    if (this.moveRight && !this.moveLeft) {
                        this.setRotYaw(this.getYaw() + 10.0F * deltaSeconds);
                    }
                }
            }
        }
    }
}
