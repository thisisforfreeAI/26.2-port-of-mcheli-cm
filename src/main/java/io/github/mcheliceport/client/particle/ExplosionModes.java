package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.ClientConfig.ExplosionMode;
import io.github.mcheliceport.config.EffectQuality;

/** Explosion-only preference; old Modern configs retain their appearance until changed. */
public final class ExplosionModes {
    private ExplosionModes() {}
    public static ExplosionMode selected() {
        return resolve(ClientConfig.get(ClientConfig.EXPLOSION_MODE), ClientConfig.get(ClientConfig.MODERN_EXPLOSIONS));
    }
    public static ExplosionMode resolve(ExplosionMode mode, boolean legacyModern) {
        return mode == ExplosionMode.CURRENT && legacyModern ? ExplosionMode.MODERN : mode;
    }
    public static void select(ExplosionMode mode) {
        ClientConfig.EXPLOSION_MODE.set(mode);
        ClientConfig.MODERN_EXPLOSIONS.set(mode == ExplosionMode.MODERN);
    }
    public static boolean customEnabled() {
        return EffectQuality.enhanced() && !ClientConfig.get(ClientConfig.DEFAULT_EXPLOSION_PARTICLE);
    }
}
