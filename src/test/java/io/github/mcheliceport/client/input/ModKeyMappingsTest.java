package io.github.mcheliceport.client.input;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.junit.jupiter.api.Test;

class ModKeyMappingsTest {
    @Test
    void ridingContextAllowsUnmodifiedBindingsWhileModifiersAreHeld() {
        // KeyModifier.NONE.isActive checks this exact relationship before accepting any key
        // while Ctrl/Shift/Alt is held. Without it even the Ctrl binding suppresses itself.
        var riding = ModKeyMappings.UP.getKeyConflictContext();
        assertTrue(riding.conflicts(KeyConflictContext.IN_GAME));
        assertTrue(riding.conflicts(riding));
        assertFalse(riding.conflicts(KeyConflictContext.GUI));
    }
}
