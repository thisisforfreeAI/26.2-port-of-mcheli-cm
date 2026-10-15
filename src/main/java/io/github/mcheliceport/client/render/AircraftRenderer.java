package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.client.AircraftClientEvents;
import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftHitBox;
import io.github.mcheliceport.entity.aircraft.AircraftPart;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.heli.HeliRotor;
import io.github.mcheliceport.util.McheliMath;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Draws vehicles with their models (render path A of PORTING_PLAN.md section 5.4). Ported from
 * upstream's {@code MCH_RenderHeli} and {@code MCH_RenderAircraft#doRender}; the part transforms
 * are in {@link AircraftPartPoses}.
 */
public abstract class AircraftRenderer<T extends AircraftEntity> extends EntityRenderer<T, AircraftRenderState> {

    /** Upstream's colour for a destroyed vehicle. */
    private static final int DESTROYED_COLOR = 0xFF262626;
    /** Upstream's colour for a vehicle ({@code 0.75} grey; {@code __TextureAlpha} is 1). */
    private static final int COLOR = 0xFFBFBFBF;

    public AircraftRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 2.0F;
    }

    @Override
    public AircraftRenderState createRenderState() {
        return new AircraftRenderState();
    }

    @Override
    public void extractRenderState(T entity, AircraftRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.draws.clear();
        state.boxes.clear();
        state.texture = null;
        io.github.mcheliceport.content.aircraft.AircraftInfo info = entity.getAcInfo();
        if (info == null) {
            return;
        }
        float[] ypr = McheliMath.slerpOrientationYPR(entity.yRotO, entity.xRotO, entity.prevRoll, entity.getYaw(),
                entity.getPitch(), entity.getRoll(), partialTicks);
        state.yaw = ypr[0];
        state.pitch = ypr[1];
        state.roll = ypr[2];
        state.testMode = ClientConfig.get(ClientConfig.TEST_MODE);
        if (state.testMode) {
            state.boxes.addAll(info.extraBoundingBox);
        }
        AircraftModels.VehicleModels models = AircraftModels.get(this.directory(), info);
        if (models == null) {
            return;
        }
        state.texture = Identifier.fromNamespaceAndPath("mcheli",
                "textures/" + this.directory() + "/" + entity.getTextureName() + ".png");
        state.color = entity.isDestroyed() ? DESTROYED_COLOR : COLOR;
        state.flat = !(info.smoothShading && ClientConfig.get(ClientConfig.SMOOTH_SHADING));
        this.drawParts(entity, info, models, input(entity, ypr, partialTicks), partialTicks, state.draws);
    }

    protected abstract String directory();

    protected abstract void drawParts(T entity, io.github.mcheliceport.content.aircraft.AircraftInfo info,
            AircraftModels.VehicleModels models, AircraftPartPoses.Input input, float partialTicks,
            List<AircraftPartPoses.Draw> draws);

    /** The pose inputs of a vehicle at a partial tick. */
    static AircraftPartPoses.Input input(AircraftEntity entity, float[] ypr, float partialTicks) {
        AircraftPart canopy = entity.partCanopy;
        AircraftPart hatch = entity.partHatch;
        AircraftPart gear = entity.partLandingGear;
        Entity cameraUser = entity.getEntityBySeatId(1);
        if (cameraUser == null) {
            cameraUser = entity.getPilot();
        }
        float cameraYaw = 0.0F;
        float cameraPitch = 0.0F;
        if (cameraUser != null) {
            cameraYaw = cameraUser.getViewYRot(partialTicks) - entity.getYaw();
            cameraPitch = cameraUser.getViewXRot(partialTicks) - entity.getPitch();
        }
        Minecraft mc = Minecraft.getInstance();
        boolean hideGunnerWeapons = mc.options.getCameraType().isFirstPerson() && mc.player != null
                && AircraftClientEvents.riddenAircraft(mc.player) == entity;
        float[][] blades = new float[0][];
        float[][] prevBlades = new float[0][];
        if (entity instanceof HeliEntity heli) {
            HeliRotor[] rotors = heli.getRotors();
            blades = new float[rotors.length][];
            prevBlades = new float[rotors.length][];
            for (int i = 0; i < rotors.length; i++) {
                int n = rotors[i].getBladeNum();
                blades[i] = new float[n];
                prevBlades[i] = new float[n];
                for (int b = 0; b < n; b++) {
                    blades[i][b] = rotors[i].getBladeRotation(b);
                    prevBlades[i][b] = rotors[i].getPrevBladeRotation(b);
                }
            }
        }
        return new AircraftPartPoses.Input(partialTicks, ypr[0], ypr[1], ypr[2],
                canopy != null ? canopy.rotation : 0.0F, canopy != null ? canopy.prevRotation : 0.0F,
                canopy != null ? canopy.rotationMax : 90.0F, canopy != null,
                hatch != null ? hatch.rotation : 0.0F, hatch != null ? hatch.prevRotation : 0.0F,
                hatch != null ? hatch.rotationMax : 90.0F, hatch != null,
                gear != null ? gear.rotation : 0.0F, gear != null ? gear.prevRotation : 0.0F, gear != null,
                entity.getCurrentThrottle(), entity.getPrevCurrentThrottle(),
                entity.rotPartRotation.clone(), entity.prevRotPartRotation.clone(),
                cameraYaw, cameraPitch, hideGunnerWeapons, blades, prevBlades,
                WeaponPartPoses.weaponParts(entity, partialTicks, mc.player, mc.options.getCameraType().isFirstPerson()),
                WeaponPartPoses.weaponBays(entity, partialTicks));
    }

    @Override
    protected AABB getBoundingBoxForCulling(T entity) {
        AABB box = entity.getBoundingBox();
        for (AircraftHitBox hitBox : entity.getHitBoxes()) {
            box = box.minmax(hitBox.getBoundingBox());
        }
        io.github.mcheliceport.content.aircraft.AircraftInfo info = entity.getAcInfo();
        if (info != null) {
            AircraftModels.VehicleModels models = AircraftModels.get(this.directory(), info);
            if (models != null) {
                // The model turns with the vehicle: cull with a cube that holds it at any attitude.
                McheliModel m = models.body();
                float r = Math.max(Math.max(Math.max(Math.abs(m.minX), Math.abs(m.maxX)),
                        Math.max(Math.abs(m.minY), Math.abs(m.maxY))), Math.max(Math.abs(m.minZ), Math.abs(m.maxZ)));
                r = r * 1.75F + 1.0F;
                box = box.minmax(new AABB(entity.getX() - r, entity.getY() - r, entity.getZ() - r, entity.getX() + r,
                        entity.getY() + r, entity.getZ() + r));
            }
        }
        return box;
    }

    @Override
    public void submit(AircraftRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (state.testMode) {
            AircraftDebugBoxes.submit(poseStack, collector, state.yaw, state.pitch, state.roll,
                    state.boundingBoxWidth, state.boundingBoxHeight, state.boxes);
        }
        if (state.texture != null && !state.draws.isEmpty()) {
            RenderType type = AircraftRenderTypes.aircraft(state.texture);
            List<AircraftPartPoses.Draw> draws = List.copyOf(state.draws);
            int color = state.color;
            int light = state.lightCoords;
            boolean flat = state.flat;
            collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
                for (AircraftPartPoses.Draw draw : draws) {
                    PoseStack.Pose p = pose.copy();
                    p.mulPose(draw.pose());
                    ModelVertices.emit(buffer, p, draw.groups(), color, light, flat);
                }
            });
        }
        super.submit(state, poseStack, collector, camera);
    }
}
