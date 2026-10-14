package io.github.mcheliceport.entity.aircraft;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * A vehicle's gunner camera: where it is, where it looks, its zoom, and the camera mode of the
 * pilot (user 0) and the first crew seat (user 1). Ported from upstream's {@code MCH_Camera}.
 *
 * <p>Both sides keep one. The client moves and turns it each tick and draws the view from it; the
 * server only keeps the modes, to give viewers in night vision the night-vision effect. The
 * client's post effect follows the mode in {@code AircraftCameraClient}, not here.
 */
public final class AircraftCamera {

    public static final int MODE_NORMAL = 0;
    public static final int MODE_NIGHTVISION = 1;
    /** Thermal vision (M7): not reachable until then, as upstream without shader support. */
    public static final int MODE_THERMALVISION = 2;
    /** Night vision given while in a camera mode is topped up when below this many ticks. */
    private static final int EFFECT_REFRESH_BELOW = 500;
    private static final int EFFECT_DURATION = 250;

    public double posX;
    public double posY;
    public double posZ;
    public float rotationYaw;
    public float rotationPitch;
    public float prevRotationYaw;
    public float prevRotationPitch;
    private float zoom = 1.0F;
    private final int[] mode = {MODE_NORMAL, MODE_NORMAL};
    private final int[] lastMode = new int[2];

    public int getUserMax() {
        return this.mode.length;
    }

    public boolean isValidUid(int uid) {
        return uid >= 0 && uid < this.getUserMax();
    }

    /** Upstream {@code initCamera}: zoom 1, normal mode, and the viewer's effect updated. */
    public void initCamera(int uid, @Nullable Entity viewer) {
        this.setCameraZoom(1.0F);
        this.setMode(uid, MODE_NORMAL);
        this.updateViewer(uid, viewer);
    }

    /** Sets a user's mode, wrapped to the modes there are (normal and night vision until M7). */
    public void setMode(int uid, int m) {
        if (this.isValidUid(uid)) {
            this.mode[uid] = m < 0 ? 0 : m % this.getModeNum(uid);
        }
    }

    public int getModeNum(int uid) {
        return 2;
    }

    public int getMode(int uid) {
        return this.isValidUid(uid) ? this.mode[uid] : MODE_NORMAL;
    }

    /** Upstream {@code getModeName}. */
    public String getModeName(int uid) {
        return switch (this.getMode(uid)) {
            case MODE_NIGHTVISION -> "NIGHT VISION";
            case MODE_THERMALVISION -> "THERMAL VISION";
            default -> "";
        };
    }

    /**
     * Upstream {@code updateViewer}, run on the server every tick for each seat: a viewer in night
     * vision gets a short night-vision effect, topped up while the mode lasts; switching back to
     * normal removes it, unless it is a longer effect from elsewhere.
     */
    public void updateViewer(int uid, @Nullable Entity viewer) {
        if (!this.isValidUid(uid) || viewer == null) {
            return;
        }
        if (viewer instanceof LivingEntity living && living.isAlive() && !living.level().isClientSide()) {
            if (this.getMode(uid) == MODE_NORMAL && this.lastMode[uid] != MODE_NORMAL) {
                MobEffectInstance effect = living.getEffect(MobEffects.NIGHT_VISION);
                if (effect != null && effect.getDuration() > 0 && effect.getDuration() < EFFECT_REFRESH_BELOW) {
                    living.removeEffect(MobEffects.NIGHT_VISION);
                }
            }
            if (this.getMode(uid) == MODE_NIGHTVISION || this.getMode(uid) == MODE_THERMALVISION) {
                MobEffectInstance effect = living.getEffect(MobEffects.NIGHT_VISION);
                if (effect == null || effect.getDuration() < EFFECT_REFRESH_BELOW) {
                    living.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, EFFECT_DURATION, 0, true, false));
                }
            }
        }
        this.lastMode[uid] = this.getMode(uid);
    }

    public void setPosition(double x, double y, double z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    public float getCameraZoom() {
        return this.zoom;
    }

    public void setCameraZoom(float z) {
        this.zoom = Math.max(z, 1.0F);
    }
}
