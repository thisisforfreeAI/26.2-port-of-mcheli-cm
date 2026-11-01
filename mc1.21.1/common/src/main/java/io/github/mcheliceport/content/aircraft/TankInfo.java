package io.github.mcheliceport.content.aircraft;

import io.github.mcheliceport.content.ContentLocation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Tank definition. Ported from upstream {@code com.norwood.mcheli.tank.MCH_TankInfo}.
 *
 * <p>Upstream also declared a 0.6 default step height for tanks, but as a private method that the
 * base class never called, so tanks default to 0 like every other vehicle; the port keeps that.
 */
public class TankInfo extends AircraftInfo {

    public int weightType = 0;
    public float weightedCenterZ = 0.0F;

    public TankInfo(ContentLocation location, String path, String parserIdentifier) {
        super(location, path, parserIdentifier);
    }

    @Override
    public List<Wheel> getDefaultWheelList() {
        List<Wheel> list = new ArrayList<>();
        list.add(new Wheel(new Vec3(1.5, -0.24, 2.0)));
        list.add(new Wheel(new Vec3(1.5, -0.24, -2.0)));
        return list;
    }

    @Override
    public float getDefaultSoundRange() {
        return 50.0F;
    }

    @Override
    public float getDefaultRotorSpeed() {
        return 47.94F;
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
            return "tank";
        }
        return seatId == 1 ? "tank" : "gunner";
    }

    @Override
    public String getDirectoryName() {
        return "tanks";
    }

    @Override
    public String getKindName() {
        return "tank";
    }
}
