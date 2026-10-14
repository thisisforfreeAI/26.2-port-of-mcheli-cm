package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.mcheliceport.content.aircraft.BoundingBoxInfo;
import io.github.mcheliceport.util.McheliMath;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * The {@code TestMode} overlay of vehicles (upstream {@code renderDebugHitBox}): the vehicle's main
 * box in white, its content bounding boxes turned with its yaw, pitch and roll in yellow, and a red
 * line for its nose. Until M4 this was the whole vehicle renderer (decision D11).
 */
public final class AircraftDebugBoxes {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int YELLOW = 0xFFFFD000;
    private static final int RED = 0xFFFF3030;

    private AircraftDebugBoxes() {
    }

    /** Draws the boxes at the entity origin. */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, float yaw, float pitch, float roll,
            float width, float height, List<BoundingBoxInfo> boxes) {
        float lineWidth = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        Quaternionf orientation = McheliMath.orientationQuat(-yaw, -pitch, -roll);
        float hw = width / 2.0F;
        float h = height;
        List<BoundingBoxInfo> copy = List.copyOf(boxes);
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
            Vec3[] main = {
                new Vec3(-hw, 0, -hw), new Vec3(hw, 0, -hw), new Vec3(hw, 0, hw), new Vec3(-hw, 0, hw),
                new Vec3(-hw, h, -hw), new Vec3(hw, h, -hw), new Vec3(hw, h, hw), new Vec3(-hw, h, hw)};
            box(buffer, pose, main, WHITE, lineWidth);
            for (BoundingBoxInfo b : copy) {
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
