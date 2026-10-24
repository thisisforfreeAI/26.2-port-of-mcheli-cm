package io.github.mcheliceport.client.gui;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Pure placement policy: leave existing pause controls and the central title untouched. */
public final class EffectsMenuLayout {
    public record Rect(int x,int y,int width,int height) {
        boolean overlaps(Rect other) {
            return x<other.x+other.width+4 && x+width+4>other.x && y<other.y+other.height+4 && y+height+4>other.y;
        }
    }
    private EffectsMenuLayout() {}
    public static @Nullable Rect entry(int width,int height,List<Rect> occupied) {
        int buttonWidth=Math.min(124,width/2-16);
        if(buttonWidth<80)return null;
        Rect title=new Rect(width/2-100,32,200,24);
        for(int y=8;y+20<=height-8;y+=24) for(int x:new int[]{width-buttonWidth-8,8}) {
            Rect candidate=new Rect(x,y,buttonWidth,20);
            if(candidate.overlaps(title))continue;
            if(occupied.stream().noneMatch(candidate::overlaps))return candidate;
        }
        return null;
    }
}
