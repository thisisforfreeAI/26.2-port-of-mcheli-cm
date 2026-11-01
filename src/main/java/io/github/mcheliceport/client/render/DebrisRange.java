package io.github.mcheliceport.client.render;

import io.github.mcheliceport.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * How far from the camera a plane's breakaway sections are spawned, tracked and drawn. A plane is
 * usually shot at from well past the near-detail radius, so the sections that tear off it must stay
 * visible out to combat range: the configured distance, never past the loaded terrain.
 */
public final class DebrisRange {
    /** The radius inside which the fine close-up detail (ribs, cables, skin chips) is added. */
    public static final int DETAIL=40;
    private DebrisRange(){}
    /** The breakaway radius in blocks. */
    public static int blocks(){
        int configured=ClientConfig.get(ClientConfig.BREAKAWAY_DEBRIS_DISTANCE);
        var mc=Minecraft.getInstance();
        int terrain=mc==null||mc.options==null?configured:mc.options.getEffectiveRenderDistance()*16;
        return clamp(configured,terrain);
    }
    static int clamp(int configured,int terrain){return Math.max(DETAIL,Math.min(configured,terrain));}
    public static double squared(){double b=blocks();return b*b;}
    public static boolean within(Vec3 at){
        var mc=Minecraft.getInstance();
        return mc.gameRenderer.mainCamera().position().distanceToSqr(at)<=squared();
    }
}
