package io.github.mcheliceport.shim.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** 1.21.1 stand-in for 26.2's {@code CameraRenderState}, read off the main camera. */
public final class CameraRenderState {
    public Vec3 pos = Vec3.ZERO;
    public Quaternionf orientation = new Quaternionf();
    public float xRot;
    public float yRot;
    public boolean initialized;
    public final Matrix4f projectionMatrix = new Matrix4f();
    public final Matrix4f viewRotationMatrix = new Matrix4f();

    private static final CameraRenderState CURRENT = new CameraRenderState();

    /** The camera as it is now; the projection is the last one the level was drawn with. */
    public static CameraRenderState current() {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        CameraRenderState state = CURRENT;
        state.pos = camera.getPosition();
        state.orientation = new Quaternionf(camera.rotation());
        state.xRot = camera.getXRot();
        state.yRot = camera.getYRot();
        state.initialized = camera.isInitialized();
        state.viewRotationMatrix.set(new Matrix4f().rotation(new Quaternionf(camera.rotation()).conjugate()));
        if (LevelProjection.captured) {
            state.projectionMatrix.set(LevelProjection.PROJECTION);
        } else {
            state.projectionMatrix.set(RenderSystem.getProjectionMatrix());
        }
        return state;
    }

    public Vec3 position() {
        return pos;
    }

    public double distanceToSqr(Vec3 point) {
        return pos.distanceToSqr(point);
    }

    public double distanceToSqr(double x, double y, double z) {
        return pos.distanceToSqr(x, y, z);
    }

    /** The level's projection, saved by the loader's after-level render hook for HUD projection. */
    public static final class LevelProjection {
        static final Matrix4f PROJECTION = new Matrix4f();
        static boolean captured;

        private LevelProjection() {
        }

        public static void capture(Matrix4f projection) {
            PROJECTION.set(projection);
            captured = true;
        }
    }
}
