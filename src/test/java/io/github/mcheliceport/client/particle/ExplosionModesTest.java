package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.ClientConfig.ExplosionMode;
import org.junit.jupiter.api.Test;

class ExplosionModesTest {
    @Test void existingModernConfigsKeepTheirLookAndExplicitNtmWins() {
        assertEquals(ExplosionMode.CURRENT, ClientConfig.EXPLOSION_MODE.getDefault());
        assertEquals(ExplosionMode.MODERN, ExplosionModes.resolve(ExplosionMode.CURRENT,true));
        assertEquals(ExplosionMode.CURRENT, ExplosionModes.resolve(ExplosionMode.CURRENT,false));
        assertEquals(ExplosionMode.NTM_NEXT, ExplosionModes.resolve(ExplosionMode.NTM_NEXT,true));
        assertEquals(ExplosionMode.NTM_NEXT, ExplosionModes.resolve(ExplosionMode.NTM_NEXT,false));
        assertEquals(ExplosionMode.MODERN, ExplosionModes.resolve(ExplosionMode.MODERN,false));
    }
}
