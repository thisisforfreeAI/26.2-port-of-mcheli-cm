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
}
