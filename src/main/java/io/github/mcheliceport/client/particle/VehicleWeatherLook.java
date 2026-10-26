package io.github.mcheliceport.client.particle;

/** Gradual wetting/drying and cold parked snow; motion sheds rather than instantly clears it. */
public final class VehicleWeatherLook {
    private float wet,snow;
    public void tick(boolean rain,boolean snowing,boolean cold,boolean exposed,boolean moving){
        wet=Math.clamp(wet+(rain&&exposed?.009F:-.0012F),0,1);
        snow=Math.clamp(snow+(snowing&&exposed&&!moving?.01F:moving?-.03F:cold?-.0002F:-.003F),0,1);
    }
    public float wet(){return wet;}
    public float snow(){return snow;}
    public int tint(int color){return io.github.mcheliceport.client.model.VehicleSurfaceShade.tint(color,1-wet*.16F);}
}
