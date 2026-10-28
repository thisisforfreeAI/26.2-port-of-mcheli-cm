package io.github.mcheliceport.entity.plane;

import io.github.mcheliceport.entity.aircraft.AircraftAttitude;

/** MCH_EntityPlane's client-side destroyed attitude, independent of authoritative movement. */
public final class PlaneVisualMotion {
    private PlaneVisualMotion() {
    }

    public static int destroyedRollDirection(float roll) {
        return roll >= 0.0F ? (roll > 90.0F ? -1 : 1) : (roll > -90.0F ? -1 : 1);
    }

    public static void destroyed(AircraftAttitude.Rotatable body, boolean nearGround, float pitchStep, float rollStep) {
        if (!nearGround) {
            if (Math.abs(body.getPitch()) < 10.0F) {
                body.setPitch(body.getPitch() + pitchStep);
            }
            float roll = Math.abs(body.getRoll());
            if (roll < 45.0F || roll > 135.0F) {
                body.setRoll(body.getRoll() + rollStep);
            }
        } else if (Math.abs(body.getPitch()) > 20.0F) {
            body.setPitch(body.getPitch() * 0.99F);
        }
    }

    /**
     * Not upstream: a wreck that has come to rest on the ground settles onto its belly or its back
     * instead of staying propped at the angle it hit. Pitch eases to level, roll to the nearer of 0 or 180.
     */
    public static void settle(AircraftAttitude.Rotatable body) {
        float pitch = body.getPitch();
        if (Math.abs(pitch) > 2.0F) {
            body.setPitch(pitch * 0.92F);
        }
        float roll = net.minecraft.util.Mth.wrapDegrees(body.getRoll());
        float target = Math.abs(roll) > 90.0F ? Math.copySign(180.0F, roll) : 0.0F;
        if (Math.abs(target - roll) > 1.0F) {
            body.setRoll(roll + (target - roll) * 0.08F);
        }
    }
}
