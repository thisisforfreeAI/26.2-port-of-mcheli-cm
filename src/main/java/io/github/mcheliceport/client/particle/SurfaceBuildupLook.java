package io.github.mcheliceport.client.particle;

/** Cosmetic exposure history. Parking preserves dirt; water and rain wash it away. */
public final class SurfaceBuildupLook {
    private float dirt,soot;
    public void tick(boolean moving,boolean loose,boolean wet,boolean rain,boolean running){
        dirt=Math.clamp(dirt+(wet?-.025F:rain?-.0015F:moving&&loose?.0025F:0),0,1);
        soot=Math.clamp(soot+(running?.0018F:rain?-.0004F:0),0,1);
    }
    public float dirt(){return dirt;}public float soot(){return soot;}
}
