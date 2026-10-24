package io.github.mcheliceport.client.render;

import io.github.mcheliceport.content.aircraft.BoundingBoxInfo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import io.github.mcheliceport.client.model.McheliModel;
import org.joml.Matrix4f;

/** What one frame draws of a vehicle. */
public class AircraftRenderState extends EntityRenderState {
    /** Null while the models load: nothing is drawn, as upstream's on-demand mode. */
    public @Nullable Identifier texture;
    /** Thermal vision draws the vehicle white, unless it is the one being ridden. */
    public boolean thermal;
    /** Past the render distance: drawn without fog, as upstream's far pass. */
    public boolean far;
    public int color;
    public boolean flat;
    public boolean surfaceShade;
    public final List<AircraftPartPoses.Draw> draws = new ArrayList<>();
    public record EmissiveDraw(Identifier texture, Matrix4f pose, List<McheliModel.Group> groups) { }
    public final List<EmissiveDraw> emissiveDraws = new ArrayList<>();

    public boolean testMode;
    public float yaw;
    public float pitch;
    public float roll;
    public final List<BoundingBoxInfo> boxes = new ArrayList<>();
    public final List<net.minecraft.world.phys.Vec3> ropeHooks = new ArrayList<>();
    public float ropeLength;
    public final List<io.github.mcheliceport.entity.aircraft.SearchLightBeam> searchLights = new ArrayList<>();
}
