package io.github.mcheliceport.content.aircraft;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One seat of a vehicle. Ported from upstream {@code com.norwood.mcheli.aircraft.MCH_SeatInfo}.
 */
public class SeatInfo {

    public final Vec3 pos;
    public final @Nullable Vec3 unmountPos;
    public final boolean gunner;
    public final boolean switchgunner;
    public final boolean fixRot;
    public final float fixYaw;
    public final float fixPitch;
    public final float minPitch;
    public final float maxPitch;
    public final boolean rotSeat;
    private final AircraftInfo.@Nullable CameraPosition camPos;
    public final boolean invCamPos;

    public SeatInfo(Vec3 p, @Nullable Vec3 up, boolean g, AircraftInfo.@Nullable CameraPosition cp, boolean icp,
            boolean sg, boolean fr, float yaw, float pitch, float pmin, float pmax, boolean rotSeat) {
        this.camPos = cp;
        this.pos = p;
        this.unmountPos = up;
        this.gunner = g;
        this.invCamPos = icp;
        this.switchgunner = sg;
        this.fixRot = fr;
        this.fixYaw = yaw;
        this.fixPitch = pitch;
        this.minPitch = pmin;
        this.maxPitch = pmax;
        this.rotSeat = rotSeat;
    }

    public SeatInfo(Vec3 p, boolean g, AircraftInfo.@Nullable CameraPosition cp, boolean icp, boolean sg,
            boolean fr, float yaw, float pitch, float pmin, float pmax, boolean rotSeat) {
        this(p, null, g, cp, icp, sg, fr, yaw, pitch, pmin, pmax, rotSeat);
    }

    public SeatInfo(Vec3 p, boolean g, AircraftInfo.@Nullable CameraPosition cp, boolean icp, boolean sg,
            boolean fr, float yaw, float pitch, boolean rotSeat) {
        this(p, null, g, cp, icp, sg, fr, yaw, pitch, -30.0F, 70.0F, rotSeat);
    }

    public SeatInfo(Vec3 p, AircraftInfo.@Nullable CameraPosition cp, float yaw, float pitch, boolean rotSeat) {
        this(p, null, false, cp, false, false, false, yaw, pitch, -30.0F, 70.0F, rotSeat);
    }

    public SeatInfo(Vec3 p, boolean rotSeat) {
        this(p, null, false, null, false, false, false, 0.0F, 0.0F, -30.0F, 70.0F, rotSeat);
    }

    public AircraftInfo.@Nullable CameraPosition getCamPos() {
        return this.camPos;
    }

    @Override
    public String toString() {
        return "SeatInfo{pos=" + this.pos + ", gunner=" + this.gunner + ", switchgunner=" + this.switchgunner
                + ", fixRot=" + this.fixRot + ", fixYaw=" + this.fixYaw + ", fixPitch=" + this.fixPitch
                + ", minPitch=" + this.minPitch + ", maxPitch=" + this.maxPitch + ", rotSeat=" + this.rotSeat
                + ", camPos=" + this.camPos + ", invCamPos=" + this.invCamPos + '}';
    }
}
