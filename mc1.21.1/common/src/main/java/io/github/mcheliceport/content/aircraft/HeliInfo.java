package io.github.mcheliceport.content.aircraft;

import io.github.mcheliceport.content.ContentLocation;
import java.util.ArrayList;
import java.util.List;

/**
 * Helicopter definition. Ported from upstream {@code com.norwood.mcheli.helicopter.MCH_HeliInfo}.
 */
public class HeliInfo extends AircraftInfo {

    public boolean isEnableFoldBlade = false;
    public final List<Rotor> rotorList = new ArrayList<>();

    public HeliInfo(ContentLocation location, String path, String parserIdentifier) {
        super(location, path, parserIdentifier);
    }

    @Override
    public float getMinRotationPitch() {
        return -20F;
    }

    @Override
    public float getMaxRotationPitch() {
        return 20F;
    }

    @Override
    public float getDefaultSoundRange() {
        return 80.0F;
    }

    @Override
    public float getDefaultRotorSpeed() {
        return 79.99F;
    }

    @Override
    public int getDefaultMaxZoom() {
        return 8;
    }

    @Override
    public String getDefaultHudName(int seatId) {
        if (seatId <= 0) {
            return "heli";
        }
        return seatId == 1 ? "heli_gnr" : "gunner";
    }

    @Override
    public String getDirectoryName() {
        return "helicopters";
    }

    @Override
    public String getKindName() {
        return "helicopter";
    }

    public static class Rotor extends DrawnPart {

        public final int bladeNum;
        public final int bladeRot;
        public final boolean haveFoldFunc;
        public final boolean oldRenderMethod;

        public Rotor(DrawnPart other, int bladeNum, int bladeRot, boolean haveFoldFunc, boolean oldRenderMethod) {
            super(other);
            this.bladeNum = bladeNum;
            this.bladeRot = bladeRot;
            this.haveFoldFunc = haveFoldFunc;
            this.oldRenderMethod = oldRenderMethod;
        }

        public Rotor(int bladeNum, int bladeRot, float x, float y, float z, float rx, float ry, float rz,
                String model, boolean haveFoldFunc, boolean oldRenderMethod) {
            super(x, y, z, rx, ry, rz, model);
            this.bladeNum = bladeNum;
            this.bladeRot = bladeRot;
            this.haveFoldFunc = haveFoldFunc;
            this.oldRenderMethod = oldRenderMethod;
        }
    }
}
