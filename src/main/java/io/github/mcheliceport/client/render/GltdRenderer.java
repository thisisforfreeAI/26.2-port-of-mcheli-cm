package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.client.model.BulletModels;
import io.github.mcheliceport.entity.gltd.GltdEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Upstream {@code MCH_RenderGLTD}: the tripod turned to its heading, the head turned with the
 * operator's view (hidden from the operator in first person), and a flickering green laser 100
 * blocks along the view.
 */
public class GltdRenderer extends EntityRenderer<GltdEntity, GltdRenderer.State> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mcheli", "textures/gltd.png");
    private static final float BEAM_START_HALF_WIDTH = 0.002F;
    private static final float BEAM_END_HALF_WIDTH = 0.15F;
    private static final Random RAND = new Random();

    public static class State extends EntityRenderState {
        public final List<AircraftPartPoses.Draw> draws = new ArrayList<>();
        public Matrix4f laser = new Matrix4f();
        public int laserColor;
    }

    public GltdRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected AABB getBoundingBoxForCulling(GltdEntity entity) {
        return entity.getBoundingBox().inflate(150.0);
    }

    @Override
    public void extractRenderState(GltdEntity entity, State state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.draws.clear();
        var model = BulletModels.get("gltd");
        Minecraft mc = Minecraft.getInstance();
        boolean hideHead = false;
        Entity rider = entity.operator();
        if (rider != null) {
            entity.isUsedPlayer = true;
            entity.renderRotationYaw = rider.getViewYRot(partial);
            entity.renderRotationPitch = rider.getViewXRot(partial);
            hideHead = rider == mc.player && mc.options.getCameraType().isFirstPerson();
        }
        float yaw = Mth.rotLerp(partial, entity.yRotO, entity.getYRot());
        GlMatrixStack gl = new GlMatrixStack();
        gl.translate(0.0, 0.25, 0.0);
        if (entity.isUsedPlayer) {
            gl.pushMatrix();
            gl.rotate(-yaw, 0.0F, 1.0F, 0.0F);
            if (model != null) {
                state.draws.add(new AircraftPartPoses.Draw(gl.current(), model.part("$body")));
            }
            gl.popMatrix();
        } else {
            gl.rotate(-yaw, 0.0F, 1.0F, 0.0F);
            if (model != null) {
                state.draws.add(new AircraftPartPoses.Draw(gl.current(), model.part("$body")));
            }
        }
        gl.translate(0.0, 0.45, 0.0);
        if (entity.isUsedPlayer) {
            gl.rotate(entity.renderRotationYaw, 0.0F, -1.0F, 0.0F);
            gl.rotate(entity.renderRotationPitch, 1.0F, 0.0F, 0.0F);
        }
        gl.translate(0.0, -0.45, 0.0);
        if (!hideHead && model != null) {
            state.draws.add(new AircraftPartPoses.Draw(gl.current(), model.part("$head")));
        }
        gl.translate(0.0, 0.45, 0.0);
        state.laser = new Matrix4f(gl.current());
        state.laserColor = 0x6080FF80 | RAND.nextInt(64) << 24;
    }

    @Override
    public void submit(State state, PoseStack stack, SubmitNodeCollector collector, CameraRenderState camera) {
        var draws = List.copyOf(state.draws);
        int light = state.lightCoords;
        collector.submitCustomGeometry(stack, AircraftRenderTypes.aircraft(TEXTURE), (pose, buffer) -> {
            for (var draw : draws) {
                var p = pose.copy();
                p.mulPose(draw.pose());
                ModelVertices.emit(buffer, p, draw.groups(), 0xFFBFBFBF, light, false);
            }
        });
        Matrix4f laser = state.laser;
        int color = state.laserColor;
        collector.submitCustomGeometry(stack, SearchLightRenderer.TYPE, (pose, buffer) -> {
            var p = pose.copy();
            p.mulPose(laser);
            // Two crossed strips from (0, 0.2, 0) to (0, 0.2, 100), widening with distance so that,
            // like upstream's GL line, the beam stays about a pixel wide from the designator.
            float[][] axes = {{1.0F, 0.0F}, {0.0F, 1.0F}};
            for (float[] a : axes) {
                float nx = a[0] * BEAM_START_HALF_WIDTH;
                float ny = a[1] * BEAM_START_HALF_WIDTH;
                float fx = a[0] * BEAM_END_HALF_WIDTH;
                float fy = a[1] * BEAM_END_HALF_WIDTH;
                buffer.addVertex(p, -nx, 0.2F - ny, 0.0F).setColor(color);
                buffer.addVertex(p, nx, 0.2F + ny, 0.0F).setColor(color);
                buffer.addVertex(p, fx, 0.2F + fy, 100.0F).setColor(color);
                buffer.addVertex(p, -nx, 0.2F - ny, 0.0F).setColor(color);
                buffer.addVertex(p, fx, 0.2F + fy, 100.0F).setColor(color);
                buffer.addVertex(p, -fx, 0.2F - fy, 100.0F).setColor(color);
            }
        });
        super.submit(state, stack, collector, camera);
    }
}
