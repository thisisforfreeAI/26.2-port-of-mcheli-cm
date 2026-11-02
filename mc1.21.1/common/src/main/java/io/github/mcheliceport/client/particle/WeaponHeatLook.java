package io.github.mcheliceport.client.particle;

/** Cosmetic temperature accumulates from real client shot events and cools continuously. */
public final class WeaponHeatLook {
    private float heat;private long tick=Long.MIN_VALUE,lastShot=Long.MIN_VALUE;
    public void tick(long now){if(tick!=Long.MIN_VALUE){long dt=now-tick;if(dt<0){heat=0;lastShot=Long.MIN_VALUE;}else heat*=(float)Math.pow(.996,Math.min(dt,1200));}tick=now;}
    public boolean shot(long now,float power){tick(now);if(now==lastShot)return false;lastShot=now;heat=Math.min(1,heat+.10F+Math.min(100,Math.max(0,power))*.001F);return true;}
    public float heat(){return heat;}
    public boolean cooling(long now){return heat>.25F&&lastShot!=Long.MIN_VALUE&&now-lastShot>8;}
    public int color(){float t=heat*heat;return ((int)(100*t)<<24)|(Math.round(115-35*t)<<16)|(Math.round(104-39*t)<<8)|Math.round(115-53*t);}
}
