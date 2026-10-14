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
    public static void emit(VertexConsumer buffer, PoseStack.Pose pose, List<McheliModel.Group> groups, int color,
            int light, boolean flat) {
        for (McheliModel.Group group : groups) {
            float[] d = group.data();
            int stride = McheliModel.STRIDE;
            for (int t = 0; t + 3 * stride <= d.length; t += 3 * stride) {
                int last = t + 2 * stride;
                for (int k = 0; k < 4; k++) {
                    int i = t + Math.min(k, 2) * stride;
                    int n = flat ? last : i;
                    buffer.addVertex(pose, d[i], d[i + 1], d[i + 2])
                            .setColor(color)
                            .setUv(d[i + 3], d[i + 4])
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light)
                            .setNormal(pose, d[n + 5], d[n + 6], d[n + 7]);
                }
            }
        }
    }
}
