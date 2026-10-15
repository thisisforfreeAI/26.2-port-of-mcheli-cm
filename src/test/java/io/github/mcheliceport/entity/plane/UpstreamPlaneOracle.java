package io.github.mcheliceport.entity.plane;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Transcribed from MCH_EntityPlane#onUpdate_Server at cbaff7f30e549ff75e485388c24c74a4316c2228.
 * Upstream McHeli CE authors; GPLv3. Only world queries, naming and collision plumbing are adapted.
 * Keep this independent of PlaneFlightModel so formula/order changes are caught by tick comparisons.
 */
final class UpstreamPlaneOracle extends PlaneFlightModelTest.State {
    private static Vec3 direction(float yaw, float pitch) {
        return new Vec3(-Mth.sin(yaw / 180.0F * (float) Math.PI) * Mth.cos(pitch / 180.0F * (float) Math.PI),
                -Mth.sin(pitch / 180.0F * (float) Math.PI),
                Mth.cos(yaw / 180.0F * (float) Math.PI) * Mth.cos(pitch / 180.0F * (float) Math.PI));
    }
    void physics() {
        double prevMotion = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
        double dp = 0.0;
        if (this.floating) {
            dp = this.depth;
        }

        boolean levelOff = this.gunner;
        if (dp == 0.0) {
            if (this.drone && this.fuel && !this.destroyed) {
                boolean block = this.blockWithin(3, -40);
                if (block) {
                    block = this.blockWithin(3, -5);
                    if (!block) {
                        this.setYaw(this.yaw + p.autoPilotRot() * 2.0F);
                        if (this.pitch > -20.0F) {
                            this.setPitch(this.pitch - 0.5F);
                        }
                    }
                } else {
                    this.setYaw(this.yaw + p.autoPilotRot());
                    this.setPitch(this.pitch * 0.95F);
                    // Landing-gear action is tested in the entity tests.

                    levelOff = true;
                }
            }

            if (!levelOff) {
                this.motionY = this.motionY +
                        (0.04 + (!this.water ? p.gravity() : p.gravityInWater()));
                this.motionY = this.motionY + -0.047 * (1.0 - this.throttle);
            } else {
                this.motionY *= 0.8;
            }
        } else {
            float f = this.pitch * 0.8F;
            this.setPitch(f);
            if (Mth.abs(this.roll) < 40.0F) {
                this.setRoll(this.roll * 0.9F);
            }

            if (dp < 1.0) {
                this.motionY -= 1.0E-4;
                this.motionY = this.motionY + 0.007 * this.throttle;
            } else {
                if (this.motionY < 0.0) {
                    this.motionY /= 2.0;
                }

                this.motionY += 0.007;
            }
        }

        float throttle = (float) (this.throttle / 10.0);
        Vec3 v;
        if (this.nozzle > 0.001F) {
            this.setPitch(this.pitch * 0.95F);
            v = direction(this.yaw, this.pitch - this.nozzle);
            if (this.nozzle >= 90.0F) {
                v = new Vec3(v.x * 0.8F, v.y, v.z * 0.8F);
            }
        } else {
            v = direction(this.yaw, this.pitch - 10.0F);
        }

        if (!levelOff) {
            if (this.nozzle <= 0.01F) {
                this.motionY = this.motionY + v.y * throttle / 2.0;
            } else {
                this.motionY = this.motionY + v.y * throttle / 8.0;
            }
        }

        boolean canMove = true;
        if (!p.canMoveOnGround()) {
            if (this.groundSolid) {
                canMove = false;
            }
        }

        if (canMove) {
            if (p.enableBack() && this.back > 0.0F) {
                this.motionX = this.motionX - v.x * this.back;
                this.motionZ = this.motionZ - v.z * this.back;
            } else {
                this.motionX = this.motionX + v.x * throttle;
                this.motionZ = this.motionZ + v.z * throttle;
            }
        }

        double motion = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
        float speedLimit = p.speedLimit(this.wing);
        if (motion > speedLimit) {
            this.motionX *= speedLimit / motion;
            this.motionZ *= speedLimit / motion;
            motion = speedLimit;
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

        if (this.grounded || this.blockWithin(1, -2)) {
            this.motionX = this.motionX * p.motionFactor();
            this.motionZ = this.motionZ * p.motionFactor();
            if (Mth.abs(this.pitch) < 40.0F) {
                this.pitch = (this.pitch - p.onGroundPitch()) * 0.8F + p.onGroundPitch();
            }
        }

        this.move( this.motionX, this.motionY, this.motionZ);
        this.motionY *= 0.95;
        this.motionX = this.motionX * p.motionFactor();
        this.motionZ = this.motionZ * p.motionFactor();
    }
}
