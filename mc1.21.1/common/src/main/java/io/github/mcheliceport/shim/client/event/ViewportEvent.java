package io.github.mcheliceport.shim.client.event;

import net.minecraft.client.Camera;

/** NeoForge's camera events: field of view and camera angles. */
public abstract class ViewportEvent extends ClientEvent {
    private final Camera camera;
    private final double partialTick;

    protected ViewportEvent(Camera camera, double partialTick) {
        this.camera = camera;
        this.partialTick = partialTick;
    }

    public CameraView getCamera() {
        return new CameraView(camera);
    }

    public double getPartialTick() {
        return partialTick;
    }

    /** The parts of 26.2's {@code Camera} the listeners read. */
    public record CameraView(Camera camera) {
        public net.minecraft.world.entity.Entity entity() {
            return camera.getEntity();
        }

        public net.minecraft.world.phys.Vec3 position() {
            return camera.getPosition();
        }

        public boolean isDetached() {
            return camera.isDetached();
        }
    }

    public static final class ComputeFov extends ViewportEvent {
        private final boolean usedConfiguredFov;
        private double fov;

        public ComputeFov(Camera camera, double partialTick, double fov, boolean usedConfiguredFov) {
            super(camera, partialTick);
            this.fov = fov;
            this.usedConfiguredFov = usedConfiguredFov;
        }

        public double getFOV() {
            return fov;
        }

        public void setFOV(double fov) {
            this.fov = fov;
        }

        public boolean usedConfiguredFov() {
            return usedConfiguredFov;
        }
    }

    public static final class ComputeCameraAngles extends ViewportEvent {
        private float yaw;
        private float pitch;
        private float roll;

        public ComputeCameraAngles(Camera camera, double partialTick, float yaw, float pitch, float roll) {
            super(camera, partialTick);
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }

        public float getYaw() {
            return yaw;
        }

        public void setYaw(float yaw) {
            this.yaw = yaw;
        }

        public float getPitch() {
            return pitch;
        }

        public void setPitch(float pitch) {
            this.pitch = pitch;
        }

        public float getRoll() {
            return roll;
        }

        public void setRoll(float roll) {
            this.roll = roll;
        }
    }
}
