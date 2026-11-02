package io.github.mcheliceport.content.aircraft;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An entity rack: a seat that carries mobs or other entities instead of a player. Ported from
 * upstream {@code com.norwood.mcheli.aircraft.MCH_SeatRackInfo}.
 */
public class SeatRackInfo extends SeatInfo {

    public final float range;
    public final float openParaAlt;
    public final String[] names;

    public SeatRackInfo(String[] entityNames, double x, double y, double z, @Nullable Vec3 up,
            AircraftInfo.CameraPosition ep, float rng, float paraAlt, float yaw, float pitch, boolean rotSeat) {
        super(new Vec3(x, y, z), up, false, ep, false, false, false, yaw, pitch, -30.0F, 70.0F, rotSeat);
        this.range = rng;
        this.openParaAlt = paraAlt;
        this.names = entityNames;
    }

    public SeatRackInfo(String[] entityNames, double x, double y, double z, AircraftInfo.CameraPosition ep,
            float rng, float paraAlt, float yaw, float pitch, boolean rotSeat) {
        this(entityNames, x, y, z, null, ep, rng, paraAlt, yaw, pitch, rotSeat);
    }

    /** Where entities get on, stored in the seat's camera position as upstream does. */
    public Vec3 getEntryPos() {
        AircraftInfo.CameraPosition camPos = this.getCamPos();
        return camPos != null ? camPos.pos() : this.pos;
    }
}
