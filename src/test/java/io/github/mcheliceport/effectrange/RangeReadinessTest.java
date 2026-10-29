package io.github.mcheliceport.effectrange;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RangeReadinessTest {
    @Test void aFastClientClockCannotReleaseTheServersCooldown(){
        var state=new RangeReadiness();state.notice(0,0,100);state.notice(2,220,100);
        assertTrue(state.ready());assertTrue(state.cooling());assertEquals(0,state.countdown(500));assertTrue(state.cooling());
        state.notice(4,0,501);assertFalse(state.cooling());assertTrue(state.ready());
    }
    @Test void optionsAndCooldownNoticesCannotDeclareAnUnfinishedPadReady(){
        var state=new RangeReadiness();state.notice(1,220,100);state.notice(3,150,130);
        assertFalse(state.ready());assertTrue(state.cooling());state.notice(4,0,300);
        assertFalse(state.ready());assertFalse(state.cooling());state.notice(0,0,310);assertTrue(state.ready());
        state.notice(999,0,320);assertTrue(state.ready());
    }
}
