package io.github.mcheliceport.client.particle;

/** Cheap silhouette/lighting decisions; no extra particles, geometry or world samples. */
public final class BlastReadability {
    private BlastReadability() {}
    private static float smooth(float t) {t=Math.clamp(t,0,1);return t*t*(3-2*t);}
    public static int distantLobes(int count, double distanceSquared) {
        if(count<=2 || !Double.isFinite(distanceSquared))return Math.min(2,count);
        float far=smooth((float)((Math.sqrt(Math.max(0,distanceSquared))-48)/112));
        return Math.max(2,Math.round(count*(1-.55F*far)));
    }
    public static float pocketOpacity(float age,int fireTicks,float phase) {
        if(fireTicks<=0 || age<0 || age>=fireTicks)return 1;
        float burial=smooth((age/fireTicks-.3F)/.7F);
        float roll=.85F+.15F*(float)Math.sin(age*.63F+phase);
        return (1-.38F*burial)*roll;
    }
    public static float amber(float age,int duration) {
        if(duration<=0 || age<0 || age>=duration)return 0;
        float fall=1-smooth(age/duration);
        return fall*(.75F+.25F*(float)Math.sin(age*.6F));
    }
    public static int smokeFrame(float age,int fireTicks,int life,int phase) {
        return 12+Math.floorMod(phase+(int)(Math.max(0,age-fireTicks)*4/Math.max(1,life-fireTicks)),4);
    }
}
