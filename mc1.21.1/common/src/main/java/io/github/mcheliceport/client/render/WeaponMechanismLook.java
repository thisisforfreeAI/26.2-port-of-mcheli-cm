package io.github.mcheliceport.client.render;

import java.util.Locale;

/** Client presentation curves; authoritative recoil buffers, cooldowns and ammunition stay untouched. */
public final class WeaponMechanismLook {
    private WeaponMechanismLook(){}
    public static float recoil(float value){float v=Float.isFinite(value)?Math.clamp(value,0,1):0;return v*(2-v);}
    public static float bay(float angle){float t=Float.isFinite(angle)?Math.clamp(angle/45,0,1):0;return 90*t*t*(3-2*t);}
    public static float barrel(float previous,float current,float previousStep,float partial){
        if(!Float.isFinite(previous+current+previousStep+partial))return 0;
        float t=Math.clamp(partial,0,1),step=current-previous;
        if(step<=0||step>90)return previous+step*t;
        float speed=Math.clamp(previousStep,0,step*3);
        float value=previous+step*(-2*t*t*t+3*t*t)+speed*(t*t*t-2*t*t+t)+step*(t*t*t-t*t);
        return Math.clamp(value,previous,current);
    }
    /** Known installations keep spent cases/stubs inside the vehicle instead of ejecting them at the muzzle. */
    public static boolean retainsCases(String name){String n=name==null?"":name.toLowerCase(Locale.ROOT);return n.startsWith("gau-8")||n.startsWith("a10gau8")||n.startsWith("m61")||n.startsWith("gau-22")||n.startsWith("rehinmetall")||n.startsWith("rheinmetall");}
    public static float caseScale(String name){
        String n=name==null?"":name.toLowerCase(Locale.ROOT);
        if(n.contains("m230"))return 1.13F;
        if(n.startsWith("m242"))return 1.37F;
        if(n.contains("2a42")||n.contains("30mm"))return 1.65F;
        if(n.contains("m134")||n.contains("m240")||n.contains("m60")||n.contains("minigun")||n.contains("7.62")||n.contains("7_62"))return .51F;
        if(n.contains("20mm")||n.startsWith("m2hb")||n.startsWith("m2_")||n.equals("m2")||n.contains("12.7"))return 1;
        return .35F; // Unrecognized addon weapons retain the existing fallback size.
    }
    /** Only known weapons using the standard mesh are capped; smaller authored cases remain smaller. */
    public static float authoredCaseScale(String name,float authored){
        float known=caseScale(name);
        return known==.35F?authored:Math.min(authored,known);
    }
}
