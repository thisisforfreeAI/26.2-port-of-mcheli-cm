package io.github.mcheliceport.client.gui;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
class EffectsMenuLayoutTest {
    @Test void entryStaysInBoundsAndAvoidsControlsAtSmallAndLargeGuiSizes() {
        for(int[] size:new int[][]{{320,240},{640,360},{960,540}}) {
            var occupied=new ArrayList<EffectsMenuLayout.Rect>();
            occupied.add(new EffectsMenuLayout.Rect(size[0]/2-102,70,204,140));
            for(int i=0;i<2;i++) {
                var r=EffectsMenuLayout.entry(size[0],size[1],occupied);assertNotNull(r);
                assertTrue(r.x()>=0 && r.y()>=0 && r.x()+r.width()<=size[0] && r.y()+r.height()<=size[1]);
                assertTrue(occupied.stream().noneMatch(r::overlaps));occupied.add(r);
            }
        }
        assertNull(EffectsMenuLayout.entry(120,120,List.of()));
        assertNull(EffectsMenuLayout.entry(320,240,List.of(new EffectsMenuLayout.Rect(0,0,320,240))));
    }
}
