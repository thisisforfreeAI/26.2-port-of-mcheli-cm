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
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
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
    private static final int COLOR = io.github.mcheliceport.client.model.VehicleSurfaceShade.BASE_COLOR;

    public AircraftRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 2.0F;
    }

    @Override
    protected float getShadowRadius(AircraftRenderState state) {
        return state.contactShadowRadius;
    }

    @Override
    public AircraftRenderState createRenderState() {
        return new AircraftRenderState();
    }

    @Override
    public void extractRenderState(T entity, AircraftRenderState state, float partialTicks) {
        state.contactShadowRadius = 2;
        if (io.github.mcheliceport.config.EffectQuality.enhanced()
                && ClientConfig.get(ClientConfig.VEHICLE_CONTACT_SHADOWS)
                && this.entityRenderDispatcher.camera != null
                && this.entityRenderDispatcher.distanceToSqr(entity) < 256) {
            var budget = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
            int pressure = io.github.mcheliceport.client.particle.EffectPerformance.pressure();
            if (budget != ClientConfig.EffectBudget.LOW && pressure == 0) {
                double wheelReach = 0;
                var contactInfo = entity.getAcInfo();
                if (contactInfo != null) for (int i = 0; i < Math.min(16, contactInfo.wheels.size()); i++) {
                    var wheel = contactInfo.wheels.get(i);
                    wheelReach = Math.max(wheelReach, Math.hypot(wheel.pos().x, wheel.pos().z));
                }
                float contact = VehicleContactLook.radius(entity.getBbWidth(), wheelReach, budget, pressure);
                state.contactShadowRadius = 2+(contact-2)*VehicleContactLook.distanceWeight(this.entityRenderDispatcher.distanceToSqr(entity));
            }
        }
        super.extractRenderState(entity, state, partialTicks);
        state.draws.clear();
        state.impactDraws.clear();
        state.emissiveDraws.clear();state.detailDraws.clear();
        state.searchLights.clear();
        state.boxes.clear();
        state.ropeHooks.clear(); state.ropeLength = 0;
        state.texture = null;
        state.thermal = ThermalVision.drawsWhite(entity);
        state.far = this.entityRenderDispatcher.camera != null
                && FarVehicles.isFar(this.entityRenderDispatcher.distanceToSqr(entity));
        io.github.mcheliceport.content.aircraft.AircraftInfo info = entity.getAcInfo();
        if (info == null) {
            return;
        }
        float[] ypr = McheliMath.slerpOrientationYPR(entity.yRotO, entity.xRotO, entity.prevRoll, entity.getYaw(),
                entity.getPitch(), entity.getRoll(), partialTicks);
        state.yaw = ypr[0];
        state.pitch = ypr[1];
        state.roll = ypr[2];
        if (entity.isSearchLightOn()) {
            for (int lamp = 0; lamp < info.searchLights.size(); lamp++) {
                var light = info.searchLights.get(lamp);
                float strength = VehicleLightDamage.strength(entity, lamp);
                if (strength > 0 && io.github.mcheliceport.entity.aircraft.SearchLightBeam.usable(light)) {
                    state.searchLights.add(io.github.mcheliceport.entity.aircraft.SearchLightBeam.of(light,
                            ypr[0], ypr[1], ypr[2], entity.searchLightYaw(partialTicks), entity.searchLightPitch(partialTicks),
                            Mth.lerp(partialTicks, entity.previousSteeringYaw, entity.steeringYaw)).dim(strength));
                }
            }
        }
        if (entity.ropes().active()) {
            state.ropeLength = entity.ropeLength();
            for (var hook : info.repellingHooks) {
                state.ropeHooks.add(McheliMath.rotVec3(hook.pos().x, hook.pos().y, hook.pos().z, -ypr[0], -ypr[1], -ypr[2]));
            }
        }
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
        if (entity.isDestroyed()) {
            state.color = DESTROYED_COLOR;
        } else if (entity.isIronCurtainActive()) {
            // Upstream's iron curtain tint, pulsing red.
            float f = entity.getIronCurtainFactor(partialTicks);
            state.color = ARGB.colorFromFloat(1.0F, 0.8F * f, 0.4F * f, 0.4F * f);
        } else {
            state.color = COLOR;
        }
        state.surfaceShade = !state.thermal && state.color == COLOR
                && ClientConfig.get(ClientConfig.VEHICLE_SURFACE_SHADE)
                && io.github.mcheliceport.config.EffectQuality.enhanced();
        state.flat = !(info.smoothShading && ClientConfig.get(ClientConfig.SMOOTH_SHADING));
        this.drawParts(entity, info, models, input(entity, ypr, partialTicks), partialTicks, state.draws);
        GlMatrixStack eraPose = AircraftPartPoses.bodyStack(input(entity, ypr, partialTicks));
        int eraIndex = 0;
        for (var box : info.extraBoundingBox) {
            if (box.isERA) {
                if (entity.isEraActive(eraIndex)) {
                    state.draws.add(new AircraftPartPoses.Draw(eraPose.current(), models.body().part("$ERA" + eraIndex)));
                }
                eraIndex++;
            }
        }
        SmallVehicleMotion.apply(entity,state,partialTicks);
        LoosePanels.extract(entity,state);
        io.github.mcheliceport.client.particle.VehicleWeather.extract(entity,state);
        io.github.mcheliceport.client.particle.VehicleSurfaceBuildup.extract(entity,state);
        VehicleImpactMarks.extract(entity,state);
        io.github.mcheliceport.client.particle.VehicleWeaponHeat.extract(entity,state);
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
                WeaponPartPoses.weaponBays(entity, partialTicks),
                entity.previousSteeringYaw + (entity.steeringYaw - entity.previousSteeringYaw) * partialTicks,
                Mth.lerp(partialTicks, entity.previousLightHatchRotation, entity.lightHatchRotation));
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
        if (entity.ropes().active() && info != null) {
            for (int index = 0; index < info.repellingHooks.size(); index++) {
                var hook = entity.ropes().hook(index);
                box = box.minmax(new AABB(hook, hook.add(0, -entity.ropeLength(), 0)).inflate(1));
            }
        }
        if (entity.isSearchLightOn() && info != null) {
            for (var light : info.searchLights) {
                if (!io.github.mcheliceport.entity.aircraft.SearchLightBeam.usable(light)) { continue; }
                var beam = io.github.mcheliceport.entity.aircraft.SearchLightBeam.of(light, entity.getYaw(),
                        entity.getPitch(), entity.getRoll(), entity.searchLightYaw(1), entity.searchLightPitch(1), entity.steeringYaw);
                var origin = entity.position().add(beam.point(0, 0, 0));
                var end = entity.position().add(beam.point(0, light.height, 0));
                box = box.minmax(new AABB(origin, end).inflate(light.width / 2));
            }
        }
        return box;
    }

    @Override
    public void submit(AircraftRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        SearchLightRenderer.submit(state.searchLights, poseStack, collector);
        if (!state.ropeHooks.isEmpty() && state.ropeLength > 0) {
            float width = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
            var hooks = List.copyOf(state.ropeHooks); float length = state.ropeLength;
            collector.submitCustomGeometry(poseStack, net.minecraft.client.renderer.rendertype.RenderTypes.lines(), (pose, buffer) -> {
                for (var hook : hooks) {
                    buffer.addVertex(pose, (float) hook.x, (float) hook.y, (float) hook.z).setColor(0xFF000000)
                            .setNormal(pose, 0, -1, 0).setLineWidth(width);
                    buffer.addVertex(pose, (float) hook.x, (float) hook.y - length, (float) hook.z).setColor(0xFF000000)
                            .setNormal(pose, 0, -1, 0).setLineWidth(width);
                }
            });
        }
        if (state.testMode) {
            AircraftDebugBoxes.submit(poseStack, collector, state.yaw, state.pitch, state.roll,
                    state.boundingBoxWidth, state.boundingBoxHeight, state.boxes);
        }
        if (state.texture != null && !state.draws.isEmpty()) {
            Identifier texture = state.thermal ? ThermalVision.WHITE : state.texture;
            RenderType type = state.far ? FarVehicles.renderType(texture) : AircraftRenderTypes.aircraft(texture);
            List<AircraftPartPoses.Draw> draws = List.copyOf(state.draws);
            int color = state.color;
            int light = state.lightCoords;
            boolean flat = state.flat;
            boolean surfaceShade = state.surfaceShade;
            boolean baked = state.outlineColor == net.minecraft.client.renderer.entity.state.EntityRenderState.NO_OUTLINE
                    && io.github.mcheliceport.client.render.baked.BakedVehicles.submit(collector, poseStack, texture,
                            draws, color, light, flat, surfaceShade, state.far);
            if (!baked) collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
                for (AircraftPartPoses.Draw draw : draws) {
                    PoseStack.Pose p = pose.copy();
                    p.mulPose(draw.pose());
                    ModelVertices.emit(buffer, p, draw.groups(), color, light, flat, surfaceShade);
                }
            });
        }
        if(!state.detailDraws.isEmpty()){
            var batches=new java.util.LinkedHashMap<Identifier,java.util.List<AircraftRenderState.DetailDraw>>();
            for(var detail:state.detailDraws)batches.computeIfAbsent(detail.texture(),k->new java.util.ArrayList<>()).add(detail);
            int detailLight=state.lightCoords;
            for(var batch:batches.entrySet()){
                var details=List.copyOf(batch.getValue());
                collector.submitCustomGeometry(poseStack,net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(batch.getKey(),false),(pose,buffer)->{
                    for(var detail:details){var local=pose.copy();local.mulPose(detail.pose());ModelVertices.emit(buffer,local,detail.groups(),detail.color(),detailLight,false);}
                });
            }
        }
        if(!state.impactDraws.isEmpty()) {
            var marks=List.copyOf(state.impactDraws);
            int impactLight=state.lightCoords;
            collector.submitCustomGeometry(poseStack,net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(VehicleImpactMarks.TEXTURE,false),
                    (pose,buffer)->{
                for(var mark:marks){
                    PoseStack.Pose local=pose.copy();local.mulPose(mark.pose());
                    var vertices=mark.mesh().vertices();var normal=mark.mesh().normal();
                    int color=((int)(255*mark.opacity())<<24)|0xFFFFFF;
                    for(int t=0;t+2<vertices.size();t+=3)for(int k=0;k<4;k++){
                        var v=vertices.get(t+Math.min(2,k));
                        buffer.addVertex(local,v.x(),v.y(),v.z()).setColor(color)
                                .setUv((mark.tile()%4+v.u())/4F,(mark.tile()/4+v.v())/2F)
                                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                                .setLight(impactLight).setNormal(local,normal.x,normal.y,normal.z);
                    }
                }
            });
        }
        for (AircraftRenderState.EmissiveDraw draw : List.copyOf(state.emissiveDraws)) {
            Identifier texture = state.thermal ? ThermalVision.WHITE : draw.texture();
            collector.submitCustomGeometry(poseStack, state.far ? FarVehicles.renderType(texture)
                    : net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(texture),
                    (pose, buffer) -> {
                        PoseStack.Pose p = pose.copy();
                        p.mulPose(draw.pose());
                        ModelVertices.emit(buffer, p, draw.groups(), 0xFFFFFFFF,
                                net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, false);
                    });
        }
        super.submit(state, poseStack, collector, camera);
    }
}
