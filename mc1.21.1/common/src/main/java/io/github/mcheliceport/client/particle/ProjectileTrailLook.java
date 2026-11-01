package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig;
import java.util.Locale;

/** Cosmetic motor/trail profiles. Never changes thrust, guidance, fuses or payload damage. */
public final class ProjectileTrailLook {
    public record Profile(float radius,int life,float motor,boolean cruise) {}
    private ProjectileTrailLook() {}
    public static Profile profile(String name,String type,float legacySize,int life) {
        String key=name.toLowerCase(Locale.ROOT).replace("-","").replace("_","");
        boolean cruise=key.contains("agm158")||key.contains("agm84")||key.contains("agm86")||key.contains("bgm109")||key.contains("harpoon")||key.contains("jassm")||key.contains("tomahawk")||key.contains("tlam")||key.contains("cruise");
        float size=Float.isFinite(legacySize)?Math.clamp(Math.abs(legacySize)*.12F,.15F,1.4F):.45F;
        return new Profile(size*(cruise?.45F:1),Math.clamp(life,24,"rocket".equalsIgnoreCase(type)?80:96),cruise?.18F:1,cruise);
    }
    public static float weight(int age,int start,int end) {
        if(age<start||end>=0&&age>=end)return 0;
        float opening=(float)Math.clamp((age-Math.max(0,start))/2.0,0,1);
        return end<0?opening:opening*(float)Math.clamp((end-age)/6.0,0,1);
    }
    public static int birthLimit(ClientConfig.EffectBudget budget){return switch(budget){case LOW->12;case BALANCED->24;case HIGH->36;};}
    public static int samples(double distance,int requested,int pressure,boolean far) {
        if(!Double.isFinite(distance)||distance<0||distance>32||requested<1)return 0;
        int count=Math.min(Math.min(3,requested),Math.max(distance>1E-5?2:1,(int)Math.ceil(distance/.7)));
        return far||pressure>0?1:count;
    }
    /** Keep fast solid-motor segments overlapping without widening low-smoke cruise exhaust. */
    public static float radius(Profile profile,double distance,int count){
        if(!Double.isFinite(distance)||count<1)return profile.radius();
        return (float)Math.clamp(distance/count*.8,profile.radius(),profile.radius()*(profile.cruise()?1.15:1.6));
    }
    public static float growth(float age,int life,boolean wisp) {
        float t=(float)Math.clamp(age/Math.max(1,life)/(wisp?.4:.24),0,1);t=t*t*(3-2*t);
        return .35F+t*(wisp?.75F:.70F);
    }
}
