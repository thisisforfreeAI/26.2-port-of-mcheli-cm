package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.mcheliceport.client.model.McheliModel;
import java.util.List;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Writes model triangles to a buffer of an entity render type, whose pipelines draw quads. */
public final class ModelVertices {

    private ModelVertices() {
    }

    /**
     * Writes each triangle as a quad whose last corner repeats the third. With {@code flat}, every
     * corner gets the normal of the triangle's last vertex, which is what upstream's
     * {@code GL_FLAT} shade model lit the triangle with.
     */
    /** Like {@link #emit}, with each vertex's alpha scaled by its entry in {@code weights}. */
    public static void emitWeighted(VertexConsumer buffer, PoseStack.Pose pose, McheliModel.Group group, int color,
            float[] weights, int light) {
        float[] d = group.data();
        int stride = McheliModel.STRIDE, alpha = color >>> 24, rgb = color & 0xFFFFFF;
        for (int t = 0; t + 3 * stride <= d.length; t += 3 * stride) {
            for (int k = 0; k < 4; k++) {
                int i = t + Math.min(k, 2) * stride;
                int a = Math.round(alpha * Math.clamp(weights[i / stride], 0F, 1F));
                buffer.addVertex(pose, d[i], d[i + 1], d[i + 2]).setColor((a << 24) | rgb).setUv(d[i + 3], d[i + 4])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, d[i + 5], d[i + 6], d[i + 7]);
            }
        }
    }

    public static void emit(VertexConsumer buffer, PoseStack.Pose pose, List<McheliModel.Group> groups, int color,
            int light, boolean flat) {
        emit(buffer, pose, groups, color, light, flat, false);
    }

    public static void emit(VertexConsumer buffer, PoseStack.Pose pose, List<McheliModel.Group> groups, int color,
            int light, boolean flat, boolean surfaceShade) {
        for (McheliModel.Group group : groups) {
            int[] shades = surfaceShade ? io.github.mcheliceport.client.model.VehicleSurfaceShade.get(group) : null;
            float[] d = group.data();
            int stride = McheliModel.STRIDE;
            for (int t = 0; t + 3 * stride <= d.length; t += 3 * stride) {
                int last = t + 2 * stride;
                for (int k = 0; k < 4; k++) {
                    int i = t + Math.min(k, 2) * stride;
                    int n = flat ? last : i;
                    buffer.addVertex(pose, d[i], d[i + 1], d[i + 2])
                            .setColor(shades == null ? color : io.github.mcheliceport.client.model.VehicleSurfaceShade.multiply(shades[i / stride],color))
                            .setUv(d[i + 3], d[i + 4])
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light)
                            .setNormal(pose, d[n + 5], d[n + 6], d[n + 7]);
                }
            }
        }
    }
}
