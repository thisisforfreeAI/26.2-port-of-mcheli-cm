package io.github.mcheliceport.content.weapon.hbm;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * HBM {@code ExplosionVNT} settings. Upstream {@code VNTSettingContainer}: the processor definitions
 * stay as the raw YAML map, since only HBM can interpret them.
 */
public class VNTSettingContainer {

    /** The {@code Attributes} map: {@code BlockAllocator}, {@code BlockProcessor}, ..., {@code SFX}. */
    public final Map<String, Object> rawEntry;
    public @Nullable String preset;
    public ExplosionEffect explosionEffect = ExplosionEffect.standard();

    public VNTSettingContainer(Map<String, Object> rawEntry) {
        this.rawEntry = rawEntry;
    }

    /** Explosion visuals. Upstream {@code VNTSettingContainer.ExplosionEffect}. */
    public static class ExplosionEffect {

        public boolean isSmall;
        public int cloudCount = 0;
        public float cloudScale = 1.0F;
        public float cloudSpeedMult = 1.0F;
        public float waveScale = 1.0F;
        public int debrisCount = 0;
        public int debrisSize = 0;
        public int debrisRetry = 0;
        public float debrisVelocity = 0.0F;
        public float debrisHorizontalDeviation = 0.0F;
        public float debrisVerticalOffset = 0.0F;
        public float soundRange = 0.0F;

        public ExplosionEffect() {
        }

        public ExplosionEffect(boolean isSmall, int cloudCount, float cloudScale, float cloudSpeedMult,
                float waveScale, int debrisCount, int debrisSize, int debrisRetry, float debrisVelocity,
                float debrisHorizontalDeviation, float debrisVerticalOffset, float soundRange) {
            this.isSmall = isSmall;
            this.cloudCount = cloudCount;
            this.cloudScale = cloudScale;
            this.cloudSpeedMult = cloudSpeedMult;
            this.waveScale = waveScale;
            this.debrisCount = debrisCount;
            this.debrisSize = debrisSize;
            this.debrisRetry = debrisRetry;
            this.debrisVelocity = debrisVelocity;
            this.debrisHorizontalDeviation = debrisHorizontalDeviation;
            this.debrisVerticalOffset = debrisVerticalOffset;
            this.soundRange = soundRange;
        }

        public static ExplosionEffect medium() {
            return new ExplosionEffect(false, 10, 2F, 0.5F, 25F, 5, 8, 20, 0.75F, 1F, -2F, 150F);
        }

        public static ExplosionEffect standard() {
            return new ExplosionEffect(false, 15, 5F, 1F, 45F, 10, 16, 50, 1F, 3F, -2F, 200F);
        }

        public static ExplosionEffect large() {
            return new ExplosionEffect(false, 30, 6.5F, 2F, 65F, 25, 16, 50, 1.25F, 3F, -2F, 350F);
        }
    }
}
