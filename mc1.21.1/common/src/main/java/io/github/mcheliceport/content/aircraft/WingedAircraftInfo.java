package io.github.mcheliceport.content.aircraft;

import io.github.mcheliceport.content.ContentLocation;
import java.util.ArrayList;
import java.util.List;

/** Fields and defaults duplicated by the pinned upstream plane and ship definitions. */
public abstract class WingedAircraftInfo extends AircraftInfo {
    public final List<DrawnPart> nozzles = new ArrayList<>();
    public final List<PlaneInfo.Rotor> rotorList = new ArrayList<>();
    public final List<PlaneInfo.Wing> wingList = new ArrayList<>();
    public boolean isEnableVtol = false;
    public boolean isDefaultVtol;
    public float vtolYaw = 0.3F;
    public float vtolPitch = 0.2F;
    public boolean isEnableAutoPilot = false;
    public boolean isVariableSweepWing = false;
    public float sweepWingSpeed = this.speed;

    protected WingedAircraftInfo(ContentLocation location, String path, String parserIdentifier) {
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
}
