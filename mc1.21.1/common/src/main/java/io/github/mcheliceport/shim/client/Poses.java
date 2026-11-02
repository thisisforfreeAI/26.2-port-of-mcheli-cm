package io.github.mcheliceport.shim.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4fc;

/** 26.2's {@code PoseStack.Pose#mulPose(Matrix4f)}, which 1.21.1 only has on the stack. */
public final class Poses {
    private Poses() {}

    public static void mul(PoseStack.Pose pose, Matrix4fc m) {
        pose.pose().mul(m);
        Matrix3f n = pose.normal().set(pose.pose()).invert().transpose();
        for (int c = 0; c < 3; c++) {
            float x = n.get(c, 0), y = n.get(c, 1), z = n.get(c, 2);
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len > 1.0E-6F) {
                n.setColumn(c, x / len, y / len, z / len);
            }
        }
    }
}
