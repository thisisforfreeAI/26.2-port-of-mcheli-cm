package io.github.mcheliceport.effectrange;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import java.util.List;

/** Fixed production weapons and bounded pad coordinates, shared by UI, server and tests. */
public record RangeOptions(int weapon,int surface,int lighting) {
    public static final List<String> WEAPONS=List.of("hydra70","agm158","mk82","gbu57");
    public static final List<String> WEAPON_LABELS=List.of("Hydra 70 rocket","AGM-158 JASSM","Mk 82 bomb","GBU-57 penetrator");
    public static final List<String> SURFACES=List.of("Sand","Soil / grass","Stone","Water");
    public static final List<String> LIGHTING=List.of("Day","Dusk","Night");
    public static final int WIDTH=49,DEPTH=12,AIR_HEIGHT=32,AREA=WIDTH*WIDTH,TOTAL=AREA*(DEPTH+AIR_HEIGHT);
    public RangeOptions {if(!valid(weapon,surface,lighting))throw new IllegalArgumentException("Invalid range options");}
    public static boolean valid(int weapon,int surface,int lighting){return weapon>=0&&weapon<4&&surface>=0&&surface<4&&lighting>=0&&lighting<3;}
    public static boolean conventional(WeaponInfo info){return info!=null&&!info.useHBM&&info.payloadNTM==WeaponInfo.Payload.NONE&&!info.destruct;}
    public long time(){return switch(lighting){case 1->12000;case 2->18000;default->6000;};}
    /** Clear the air first, then restore supported terrain from the bottom up. No coordinate list allocation. */
    public static int[] block(int cursor){
        if(cursor<0||cursor>=TOTAL)throw new IllegalArgumentException("Invalid reset cursor");
        int layer=cursor/AREA,y=layer<AIR_HEIGHT?AIR_HEIGHT-1-layer:layer-AIR_HEIGHT-DEPTH;
        return new int[]{cursor%WIDTH-WIDTH/2,y,cursor/WIDTH%WIDTH-WIDTH/2};
    }
}
