package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.client.model.McheliModel;
import org.junit.jupiter.api.Test;
class LoosePanelLookTest {
    private static McheliModel.Group group(String name,float span,int vertices){var d=new float[vertices*8];d[0]=span;return new McheliModel.Group(name,d);}
    @Test void onlySmallNamedAuthoredPanelsQualify(){
        assertNotNull(LoosePanelLook.spec(group("$hatch0",2,6)));
        assertNull(LoosePanelLook.spec(group("$body",2,6)));
        assertNull(LoosePanelLook.spec(group("$hatch0",20,6)));
        assertNull(LoosePanelLook.spec(group("$canopy",2,603)));
    }
    @Test void oldDamageDoesNotReplayDetachmentAndRepairsAllowANewBreak(){
        var trigger=new LoosePanelLook.Trigger();
        assertFalse(trigger.tick(95,100));assertFalse(trigger.tick(95,100));
        assertFalse(trigger.tick(20,100));assertTrue(trigger.tick(95,100));assertFalse(trigger.tick(96,100));
    }
    @Test void healthyPanelsStayStillAndCriticalRattleIsRestrained(){
        for(int tick=0;tick<100;tick++){assertEquals(0,LoosePanelLook.rattle(.5F,tick,7),0F);assertTrue(Math.abs(LoosePanelLook.rattle(1,tick,7))<=.0351F);}
    }
}
