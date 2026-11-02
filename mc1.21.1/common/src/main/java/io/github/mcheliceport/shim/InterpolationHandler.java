package io.github.mcheliceport.shim;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 1.21.1 stand-in for 26.2's {@code InterpolationHandler}: smooths a client entity towards the
 * positions the server sends. On 1.21.1 the entity feeds it from {@code lerpTo} (see
 * {@link #lerpTo}); 26.2 calls it from the entity base class instead.
 */
public final class InterpolationHandler {
    public static final int DEFAULT_INTERPOLATION_STEPS = 3;

    private final Entity entity;
    private int interpolationSteps;
    private int steps;
    private Vec3 position = Vec3.ZERO;
    private float yRot;
    private float xRot;
    private @Nullable Vec3 previousTickPosition;
    private float previousYRot;
    private float previousXRot;
    private boolean hasPreviousRot;

    public InterpolationHandler(Entity entity) {
        this(entity, DEFAULT_INTERPOLATION_STEPS);
    }

    public InterpolationHandler(Entity entity, int interpolationSteps) {
        this.entity = entity;
        this.interpolationSteps = interpolationSteps;
    }

    public int getInterpolationLength() {
        return interpolationSteps;
    }

    public void setInterpolationLength(int steps) {
        this.interpolationSteps = steps;
    }

    public boolean hasActiveInterpolation() {
        return steps > 0;
    }

    public Vec3 position() {
        return steps > 0 ? position : entity.position();
    }

    public float yRot() {
        return steps > 0 ? yRot : entity.getYRot();
    }

    public float xRot() {
        return steps > 0 ? xRot : entity.getXRot();
    }

    /** 1.21.1's {@code Entity#lerpTo}: start moving towards the new server position. */
    public void lerpTo(double x, double y, double z, float yRot, float xRot) {
        interpolateTo(new Vec3(x, y, z), yRot, xRot);
    }

    public void interpolateTo(Vec3 target, float yRot, float xRot) {
        if (interpolationSteps == 0) {
            entity.moveTo(target.x, target.y, target.z, yRot, xRot);
            cancel();
            return;
        }
        this.steps = interpolationSteps;
        this.position = target;
        this.yRot = yRot;
        this.xRot = xRot;
        this.previousTickPosition = entity.position();
        this.previousYRot = entity.getYRot();
        this.previousXRot = entity.getXRot();
        this.hasPreviousRot = true;
    }

    public void interpolate() {
        if (!hasActiveInterpolation()) {
            cancel();
            return;
        }
        double alpha = 1.0 / steps;
        if (previousTickPosition != null) {
            Vec3 delta = entity.position().subtract(previousTickPosition);
            if (entity.level().noCollision(entity, entity.getBoundingBox().move(position.add(delta).subtract(entity.position())))) {
                position = position.add(delta);
            }
        }
        if (hasPreviousRot) {
            yRot += entity.getYRot() - previousYRot;
            xRot += entity.getXRot() - previousXRot;
        }
        double x = Mth.lerp(alpha, entity.getX(), position.x);
        double y = Mth.lerp(alpha, entity.getY(), position.y);
        double z = Mth.lerp(alpha, entity.getZ(), position.z);
        entity.setPos(x, y, z);
        entity.setYRot(Mth.rotLerp((float) alpha, entity.getYRot(), yRot));
        entity.setXRot(Mth.lerp((float) alpha, entity.getXRot(), xRot));
        steps--;
        previousTickPosition = entity.position();
        previousYRot = entity.getYRot();
        previousXRot = entity.getXRot();
    }

    public void cancel() {
        steps = 0;
        previousTickPosition = null;
        hasPreviousRot = false;
    }
}
