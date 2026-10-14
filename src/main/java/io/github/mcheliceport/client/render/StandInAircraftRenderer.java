package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.BoundingBoxInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftHitBox;
import io.github.mcheliceport.util.McheliMath;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * A throw-away renderer until M4 draws the models (decision D11): the vehicle's main box in
 * white, its content bounding boxes turned with its yaw, pitch and roll in yellow, and a red line
 * for its nose.
 */
public class StandInAircraftRenderer<T extends AircraftEntity> extends EntityRenderer<T, StandInAircraftRenderer.State> {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int YELLOW = 0xFFFFD000;
    private static final int RED = 0xFFFF3030;

    public StandInAircraftRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.0F;
    }

    /** What one frame draws. */
    public static class State extends EntityRenderState {
        public float yaw;
        public float pitch;
        public float roll;
        public final List<BoundingBoxInfo> boxes = new ArrayList<>();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yaw = entity.getViewYRot(partialTicks);
        state.pitch = entity.getViewXRot(partialTicks);
        state.roll = entity.getRoll(partialTicks);
        state.boxes.clear();
        AircraftInfo info = entity.getAcInfo();
        if (info != null) {
            state.boxes.addAll(info.extraBoundingBox);
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(T entity) {
        AABB box = entity.getBoundingBox();
        for (AircraftHitBox hitBox : entity.getHitBoxes()) {
            box = box.minmax(hitBox.getBoundingBox());
        }
        return box;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        float lineWidth = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        Quaternionf orientation = McheliMath.orientationQuat(-state.yaw, -state.pitch, -state.roll);
        float hw = state.boundingBoxWidth / 2.0F;
        float h = state.boundingBoxHeight;
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
            Vec3[] main = {
                new Vec3(-hw, 0, -hw), new Vec3(hw, 0, -hw), new Vec3(hw, 0, hw), new Vec3(-hw, 0, hw),
                new Vec3(-hw, h, -hw), new Vec3(hw, h, -hw), new Vec3(hw, h, hw), new Vec3(-hw, h, hw)};
            box(buffer, pose, main, WHITE, lineWidth);
            for (BoundingBoxInfo b : state.boxes) {
                double x = b.width / 2.0;
                double y = b.height / 2.0;
                double z = b.widthZ / 2.0;
                Vec3[] corners = new Vec3[8];
                for (int i = 0; i < 8; i++) {
                    double cx = b.offsetX + ((i & 1) == 0 ? -x : x);
                    double cy = b.offsetY + ((i & 4) == 0 ? -y : y);
                    double cz = b.offsetZ + ((i & 2) == 0 ? -z : z);
                    corners[i] = McheliMath.applyQuat(orientation, cx, cy, cz);
                }
                Vec3[] ordered = {corners[0], corners[1], corners[3], corners[2], corners[4], corners[5], corners[7],
                    corners[6]};
                box(buffer, pose, ordered, YELLOW, lineWidth);
            }
            Vec3 nose = McheliMath.applyQuat(orientation, 0.0, h / 2.0, Math.max(hw * 2.0, 2.0));
            line(buffer, pose, new Vec3(0, h / 2.0, 0), nose, RED, lineWidth);
        });
        super.submit(state, poseStack, collector, camera);
    }

    /** Twelve edges of a box given its bottom face and then its top face, each in order round. */
    private static void box(VertexConsumer buffer, PoseStack.Pose pose, Vec3[] c, int color, float width) {
        for (int i = 0; i < 4; i++) {
            line(buffer, pose, c[i], c[(i + 1) % 4], color, width);
            line(buffer, pose, c[i + 4], c[(i + 1) % 4 + 4], color, width);
            line(buffer, pose, c[i], c[i + 4], color, width);
        }
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, int color, float width) {
        float nx = (float) (b.x - a.x);
        float ny = (float) (b.y - a.y);
        float nz = (float) (b.z - a.z);
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0E-6F) {
            return;
        }
        nx /= length;
        ny /= length;
        nz /= length;
        buffer.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(color).setNormal(pose, nx, ny, nz)
                .setLineWidth(width);
        buffer.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(color).setNormal(pose, nx, ny, nz)
                .setLineWidth(width);
    }
}
