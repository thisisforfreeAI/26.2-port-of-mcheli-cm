package io.github.mcheliceport.content.weapon;

import java.util.ArrayList;
import java.util.List;

/**
 * Damage falloff over distance. Ported from upstream {@code MCH_IBulletDecay},
 * {@code MCH_BulletDecayFactory} and {@code MCH_BulletDecaySegmented}.
 */
public interface BulletDecay {

    float calculateDecayFactor(float distanceTraveled);

    default float calculateDamage(float initialDamage, float distanceTraveled) {
        return initialDamage * this.calculateDecayFactor(distanceTraveled);
    }

    /** Upstream {@code MCH_BulletDecayFactory#createBulletDecay}, used by the TXT format. */
    static BulletDecay create(String type, String[] args) {
        if ("Segmented".equals(type)) {
            List<Segmented.DecaySegment> segments = new ArrayList<>();
            for (int i = 0; i + 1 < args.length; i += 2) {
                segments.add(new Segmented.DecaySegment(Float.parseFloat(args[i]), Float.parseFloat(args[i + 1])));
            }
            return new Segmented(segments);
        }
        throw new IllegalArgumentException("Invalid bullet decay type: " + type);
    }

    /** Multiplier steps: past each start distance, damage is multiplied by that step's factor. */
    record Segmented(List<DecaySegment> segments) implements BulletDecay {

        @Override
        public float calculateDecayFactor(float distanceTraveled) {
            float decayFactor = 1.0F;
            for (DecaySegment segment : this.segments) {
                if (distanceTraveled > segment.startDistance()) {
                    decayFactor = segment.damageMultiplier();
                }
            }
            return decayFactor;
        }

        public record DecaySegment(float startDistance, float damageMultiplier) {
        }
    }
}
