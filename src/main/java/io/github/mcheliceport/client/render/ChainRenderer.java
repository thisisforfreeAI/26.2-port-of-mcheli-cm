package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.mcheliceport.client.model.BulletModels;
import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.entity.chain.ChainEntity;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Stock link model every .95 blocks, directed from trailer to tug; no direct GL calls. */
public class ChainRenderer extends EntityRenderer<ChainEntity, ChainRenderer.State> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mcheli", "textures/chain.png");
    public static class State extends EntityRenderState {
        public @Nullable List<McheliModel.Group> groups;
        public Vec3 start = Vec3.ZERO, delta = Vec3.ZERO;
    }
    public ChainRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override protected AABB getBoundingBoxForCulling(ChainEntity entity) {
        var first = entity.towed(); var second = entity.tow();
        return first != null && second != null ? first.getBoundingBox().minmax(second.getBoundingBox()).inflate(1) : entity.getBoundingBox();
    }
    @Override public void extractRenderState(ChainEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.groups = null;
        var first = entity.towed(); var second = entity.tow();
        if (first == null || second == null) { return; }
        Vec3 start = first.getPosition(partialTick);
        state.delta = second.getPosition(partialTick).subtract(start);
        state.start = start.subtract(entity.getPosition(partialTick));
        var model = BulletModels.get("chain");
        if (model != null) { state.groups = model.groups; }
    }
    @Override public void submit(State state, PoseStack stack, SubmitNodeCollector collector, CameraRenderState camera) {
        double distance = state.delta.length();
        if (state.groups != null && distance > 0.95 && distance <= 64) {
            Vec3 step = state.delta.scale(0.95F / distance);
            float yaw = (float) Math.toDegrees(Math.atan2(step.z, step.x));
            float pitch = (float) Math.toDegrees(Math.atan2(step.y, step.horizontalDistance()));
            var groups = state.groups; int light = state.lightCoords;
            stack.pushPose(); stack.translate(state.start.x, state.start.y, state.start.z);
            for (; distance > 0.95F; distance -= 0.95F) {
                stack.translate(step.x, step.y, step.z); stack.pushPose();
                stack.mulPose(Axis.YN.rotationDegrees(yaw)); stack.mulPose(Axis.ZP.rotationDegrees(pitch));
                collector.submitCustomGeometry(stack, AircraftRenderTypes.aircraft(TEXTURE),
                        (pose, buffer) -> ModelVertices.emit(buffer, pose, groups, 0xFF808080, light, false));
                stack.popPose();
            }
            stack.popPose();
        }
        super.submit(state, stack, collector, camera);
    }
}
