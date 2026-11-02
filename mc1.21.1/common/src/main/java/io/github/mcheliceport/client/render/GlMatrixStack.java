package io.github.mcheliceport.client.render;

import java.util.ArrayDeque;
import java.util.Deque;
import org.joml.Matrix4f;

/**
 * A matrix stack with the call semantics of upstream's {@code GlStateManager} matrix functions, so
 * part transforms can be ported call for call: {@link #rotate} takes degrees about any axis, and
 * every call multiplies on the right as OpenGL did.
 */
public final class GlMatrixStack {

    private final Deque<Matrix4f> stack = new ArrayDeque<>();
    private Matrix4f top;

    public GlMatrixStack() {
        this.top = new Matrix4f();
    }

    public GlMatrixStack(Matrix4f start) {
        this.top = new Matrix4f(start);
    }

    public void pushMatrix() {
        this.stack.push(new Matrix4f(this.top));
    }

    public void popMatrix() {
        this.top = this.stack.pop();
    }

    public void translate(double x, double y, double z) {
        this.top.translate((float) x, (float) y, (float) z);
    }

    /**
     * {@code glRotatef}: degrees about the axis (x, y, z), which need not be normalised; a zero axis
     * does nothing. The matrix is the OpenGL specification's, built in doubles: JOML's float
     * rotations derive the cosine from the sine and lose precision near half turns.
     */
    public void rotate(float angle, float x, float y, float z) {
        double len = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
        if (len == 0.0) {
            return;
        }
        double ax = x / len;
        double ay = y / len;
        double az = z / len;
        double rad = Math.toRadians(angle);
        double c = Math.cos(rad);
        double s = Math.sin(rad);
        double t = 1.0 - c;
        // Matrix4f's constructor takes columns.
        Matrix4f r = new Matrix4f(
                (float) (ax * ax * t + c), (float) (ay * ax * t + az * s), (float) (ax * az * t - ay * s), 0.0F,
                (float) (ax * ay * t - az * s), (float) (ay * ay * t + c), (float) (ay * az * t + ax * s), 0.0F,
                (float) (ax * az * t + ay * s), (float) (ay * az * t - ax * s), (float) (az * az * t + c), 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F);
        this.top.mul(r);
    }

    public void scale(float x, float y, float z) {
        this.top.scale(x, y, z);
    }

    /** A copy of the current matrix. */
    public Matrix4f current() {
        return new Matrix4f(this.top);
    }
}
