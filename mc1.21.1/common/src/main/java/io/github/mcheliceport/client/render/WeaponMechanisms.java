package io.github.mcheliceport.client.render;

import io.github.mcheliceport.config.*;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.weapon.WeaponSet;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.*;
import net.minecraft.client.Minecraft;

/** At most 128 visible spinning sets; no entity scan or extra simulation clock. */
public final class WeaponMechanisms {
    private static final class Spin {long tick;float step,previousStep;}
    private static final Map<WeaponSet,Spin> SPINS=new WeakHashMap<>();private static Object world;
    private static final Map<AircraftEntity,boolean[]> BAYS=new WeakHashMap<>();
    private WeaponMechanisms(){}
    public static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.WEAPON_MECHANISMS);}
    public static boolean conventional(WeaponInfo info){return info!=null&&!info.useHBM&&info.payloadNTM==WeaponInfo.Payload.NONE;}
    public static boolean refine(WeaponSet set){return set!=null&&set.getWeaponsCount()>0&&enabled()&&conventional(set.getInfo());}
    public static void clear(){SPINS.clear();BAYS.clear();world=null;}
    private static void checkWorld(Object level){if(world!=level){clear();world=level;}}
    public static int spins(){return SPINS.size();}
    /** Remember the selected payload while closing, so mixed-addon bays do not jump between curves. */
    public static float bay(AircraftEntity ac,int index,float angle){
        if(!enabled()||ac.getAcInfo()==null||index>=ac.getAcInfo().partWeaponBay.size())return angle;
        checkWorld(ac.level());
        var state=BAYS.get(ac);
        if(state==null){if(BAYS.size()>=128)return angle;state=new boolean[ac.getAcInfo().partWeaponBay.size()];BAYS.put(ac,state);}
        var ids=ac.getAcInfo().partWeaponBay.get(index).weaponIds;
        boolean selected=false,conventional=true;
        for(int sid=0;sid<=ac.getSeatNum();sid++){
            if(ac.getEntityBySeatId(sid)==null)continue;
            int current=ac.weapons().getWeaponIDBySeatID(sid);
            for(Integer id:ids)if(id!=null&&id==current){selected=true;conventional&=conventional(ac.weapons().getWeaponInfoById(id));}
        }
        if(selected)state[index]=conventional;
        else if(angle<=0)state[index]=false;
        return state[index]?WeaponMechanismLook.bay(angle):angle;
    }
    public static float barrel(WeaponSet set,float partial){
        var level=Minecraft.getInstance().level;checkWorld(level);
        float previous=set.getPrevRotBarrel(),current=set.getRotBarrel(),step=current-previous;
        if(level==null||!Float.isFinite(step)||step<=.001F||step>90){SPINS.remove(set);return current;}
        long now=level.getGameTime();var state=SPINS.get(set);
        if(state==null){if(SPINS.size()>=128)return previous+step*partial;state=new Spin();state.tick=now;state.previousStep=state.step=step;SPINS.put(set,state);}
        else if(state.tick!=now){state.previousStep=now-state.tick>=1&&now-state.tick<=2?state.step:step;state.step=step;state.tick=now;}
        return WeaponMechanismLook.barrel(previous,current,state.previousStep,partial);
    }
}
