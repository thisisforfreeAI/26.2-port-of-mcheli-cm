package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.client.model.BulletModels;
import io.github.mcheliceport.entity.uav.UavStationEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Stock console and portable cover transforms through the same supported pipeline as vehicles. */
public class UavStationRenderer extends EntityRenderer<UavStationEntity, UavStationRenderer.State> {
    public static class State extends EntityRenderState {
        public Identifier texture = Identifier.fromNamespaceAndPath("mcheli", "textures/uav_station.png");
        public final List<AircraftPartPoses.Draw> draws = new ArrayList<>();
    }
    public UavStationRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(UavStationEntity entity) {
        return entity.getBoundingBox().inflate(2);
    }
    @Override public void extractRenderState(UavStationEntity entity, State state, float partial) {
        super.extractRenderState(entity, state, partial); state.draws.clear();
        String name = entity.small() ? "uav_portable_controller" : "uav_station";
        boolean lit = entity.small() ? entity.cover > .95F : entity.controlled() != null && entity.operator() != null;
        state.texture = Identifier.fromNamespaceAndPath("mcheli", "textures/" + name + (lit ? "_on" : "") + ".png");
        var model = BulletModels.get(name); if (model == null) { return; }
        var gl = new GlMatrixStack(); gl.translate(0, .35, 0); gl.rotate(Mth.rotLerp(partial, entity.yRotO, entity.getYRot()), 0, -1, 0);
        if (!entity.small()) { state.draws.add(new AircraftPartPoses.Draw(gl.current(), model.groups)); return; }
        state.draws.add(new AircraftPartPoses.Draw(gl.current(), model.part("$body")));
        float cover = Mth.lerp(partial, entity.previousCover, entity.cover);
        String[] parts = {"$cover", "$laptop_cover", "$display"};
        float[] angle = {60, 95, -85}; double[] y = {-.1812, -.1808, -.1807}, z = {-.3186, -.0422, .2294};
        for (int i = 0; i < 3; i++) {
            gl.pushMatrix(); gl.translate(0, y[i], z[i]); gl.rotate(cover * angle[i], -1, 0, 0); gl.translate(0, -y[i], -z[i]);
            state.draws.add(new AircraftPartPoses.Draw(gl.current(), model.part(parts[i]))); gl.popMatrix();
        }
    }
    @Override public void submit(State state, PoseStack stack, SubmitNodeCollector collector, CameraRenderState camera) {
        var draws = List.copyOf(state.draws); int light = state.lightCoords;
        collector.submitCustomGeometry(stack, AircraftRenderTypes.aircraft(state.texture), (pose, buffer) -> {
            for (var draw : draws) {
                var p = pose.copy(); p.mulPose(draw.pose()); ModelVertices.emit(buffer, p, draw.groups(), 0xFFBFBFBF, light, false);
            }
        });
        super.submit(state, stack, collector, camera);
    }
}
