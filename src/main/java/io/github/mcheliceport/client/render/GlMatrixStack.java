package io.github.mcheliceport.client.render;

import java.util.ArrayDeque;
import java.util.Deque;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

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

    /** {@code glRotatef}: degrees about the axis (x, y, z), which need not be normalised; a zero axis does nothing. */
    public void rotate(float angle, float x, float y, float z) {
        if (x == 0.0F && y == 0.0F && z == 0.0F) {
            return;
        }
        this.top.rotate(new Quaternionf().rotationAxis((float) Math.toRadians(angle), x, y, z));
    }

    public void scale(float x, float y, float z) {
        this.top.scale(x, y, z);
    }

    /** A copy of the current matrix. */
    public Matrix4f current() {
        return new Matrix4f(this.top);
    }
}
