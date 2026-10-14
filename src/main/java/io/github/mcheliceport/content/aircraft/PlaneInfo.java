package io.github.mcheliceport.content.aircraft;

import io.github.mcheliceport.content.ContentLocation;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Plane definition. Ported from upstream {@code com.norwood.mcheli.plane.MCH_PlaneInfo}.
 *
 * <p>The part classes ({@link Blade}, {@link Pylon}, {@link Rotor}, {@link Wing}) are shared with
 * {@link ShipInfo}; upstream had identical copies in both classes.
 */
public class PlaneInfo extends AircraftInfo {

    public final List<DrawnPart> nozzles = new ArrayList<>();
    public final List<Rotor> rotorList = new ArrayList<>();
    public final List<Wing> wingList = new ArrayList<>();
    public final List<ExhaustFlame> exhaustFlames = new ArrayList<>();
    public boolean isEnableVtol = false;
    public boolean isDefaultVtol;
    public float vtolYaw = 0.3F;
    public float vtolPitch = 0.2F;
    public boolean isEnableAutoPilot = false;
    public boolean isVariableSweepWing = false;
    public float sweepWingSpeed = this.speed;

    public PlaneInfo(ContentLocation location, String path, String parserIdentifier) {
        super(location, path, parserIdentifier);
    }

    @Override
    public float getDefaultRotorSpeed() {
        return 47.94F;
    }

    public boolean haveNozzle() {
        return !this.nozzles.isEmpty();
    }

    public boolean haveRotor() {
        return !this.rotorList.isEmpty();
    }

    public boolean haveWing() {
        return !this.wingList.isEmpty();
    }

    @Override
    public float getMaxSpeed() {
        return 1.8F;
    }

    @Override
    public int getDefaultMaxZoom() {
        return 8;
    }

    @Override
    public String getDefaultHudName(int seatId) {
        if (seatId <= 0) {
            return "plane";
        }
        return seatId == 1 ? "plane" : "gunner";
    }

    @Override
    public boolean validate() throws Exception {
        if (this.haveHatch() && this.haveWing()) {
            this.wingList.clear();
            this.hatchList.clear();
        }
        return super.validate();
    }

    @Override
    public String getDirectoryName() {
        return "planes";
    }

    @Override
    public String getKindName() {
        return "plane";
    }

    public static class Blade extends DrawnPart {

        public final int numBlade;
        public final int rotBlade;

        public Blade(int num, int r, float px, float py, float pz, float rx, float ry, float rz, String name) {
            super(px, py, pz, rx, ry, rz, name);
            this.numBlade = num;
            this.rotBlade = r;
        }

        public Blade(DrawnPart other, int numBlade, int rotBlade) {
            super(other);
            this.numBlade = numBlade;
            this.rotBlade = rotBlade;
        }
    }

    public static class Pylon extends DrawnPart {

        public final float maxRotFactor;
        public final float maxRot;

        public Pylon(DrawnPart other, float maxRot) {
            super(other);
            this.maxRot = maxRot;
            this.maxRotFactor = maxRot / 90F;
        }

        public Pylon(float px, float py, float pz, float rx, float ry, float rz, float mr, String name) {
            super(px, py, pz, rx, ry, rz, name);
            this.maxRot = mr;
            this.maxRotFactor = this.maxRot / 90.0F;
        }
    }

    public static class Rotor extends DrawnPart {

        public final float maxRotFactor;
        public final List<Blade> blades = new ArrayList<>();

        public Rotor(DrawnPart other, float maxRotFactor) {
            super(other);
            this.maxRotFactor = maxRotFactor;
        }

        public Rotor(float x, float y, float z, float rx, float ry, float rz, float mrf, String model) {
            super(x, y, z, rx, ry, rz, model);
            this.maxRotFactor = mrf;
        }
    }

    public static class Wing extends DrawnPart {

        public final float maxRotFactor;
        public final float maxRot;
        public @Nullable List<Pylon> pylonList;

        public Wing(DrawnPart other, float maxRot) {
            super(other);
            this.maxRot = maxRot;
            this.maxRotFactor = maxRot / 90F;
            this.pylonList = null;
        }

        public Wing(float px, float py, float pz, float rx, float ry, float rz, float mr, String name) {
            super(px, py, pz, rx, ry, rz, name);
            this.maxRot = mr;
            this.maxRotFactor = this.maxRot / 90.0F;
            this.pylonList = null;
        }
    }

    public static class ExhaustFlame extends DrawnPart {

        public final String texturePrefix;
        public final float degreeYaw;
        public final int delay;

        public ExhaustFlame(String modelName, String texturePrefix, float x, float y, float z, float rx, float ry,
                float rz, float degreeYaw, int delay) {
            super(x, y, z, rx, ry, rz, modelName);
            this.texturePrefix = texturePrefix;
            this.degreeYaw = degreeYaw;
            this.delay = delay;
        }

        public ExhaustFlame(DrawnPart other, String texturePrefix, float degreeYaw, int delay) {
            super(other);
            this.texturePrefix = texturePrefix;
            this.degreeYaw = degreeYaw;
            this.delay = delay;
        }
    }
}
