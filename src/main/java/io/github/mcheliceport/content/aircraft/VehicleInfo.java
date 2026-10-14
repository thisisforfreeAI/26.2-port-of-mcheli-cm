package io.github.mcheliceport.content.aircraft;

import io.github.mcheliceport.content.ContentLocation;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Definition of a "vehicle": mounted guns, launchers and similar placed weapons. Ported from
 * upstream {@code com.norwood.mcheli.vehicle.MCH_VehicleInfo}.
 */
public class VehicleInfo extends AircraftInfo {

    public boolean isEnableMove = false;
    public boolean isEnableRot = false;
    public final List<VPart> partList = new ArrayList<>();

    public VehicleInfo(ContentLocation location, String path, String parserIdentifier) {
        super(location, path, parserIdentifier);
    }

    @Override
    public float getMinRotationPitch() {
        return -90.0F;
    }

    @Override
    public float getMaxRotationPitch() {
        return 90.0F;
    }

    @Override
    public String getDefaultHudName(int seatId) {
        return "vehicle";
    }

    @Override
    public String getDirectoryName() {
        return "vehicles";
    }

    @Override
    public String getKindName() {
        return "vehicle";
    }

    public static class VPart extends DrawnPart {

        public final boolean rotPitch;
        public final boolean rotYaw;
        public final int type;
        public final boolean drawFP;
        public final float recoilBuf;
        public @Nullable List<VPart> child;

        public VPart(float x, float y, float z, String model, boolean drawfp, boolean roty, boolean rotp, int type,
                float rb) {
            super(x, y, z, 0.0F, 0.0F, 0.0F, model);
            this.rotYaw = roty;
            this.rotPitch = rotp;
            this.type = type;
            this.child = null;
            this.drawFP = drawfp;
            this.recoilBuf = rb;
        }

        public VPart(DrawnPart other, boolean rotPitch, boolean rotYaw, int type, boolean drawFP, float recoilBuf) {
            super(other);
            this.rotPitch = rotPitch;
            this.rotYaw = rotYaw;
            this.type = type;
            this.drawFP = drawFP;
            this.recoilBuf = recoilBuf;
            this.child = null;
        }
    }
}
