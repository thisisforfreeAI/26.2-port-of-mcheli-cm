package io.github.mcheliceport.client.effect;

import io.github.mcheliceport.config.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/** Small ground-fitted wet patches, with bounded terrain sampling and dry-out. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class FluidStains {
    private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("mcheli_ce_port","textures/effect/m8_fluid_stains.png");
    private static final List<Stain> MARKS=new ArrayList<>();
    private static ClientLevel world;
    private static long sampleTick=Long.MIN_VALUE;
    private static int rays,cursor;
    private record Stain(BlockPos pos,BlockState state,double x,double y,double z,double left,double right,double back,double front,
            float radius,boolean coolant,long born,long wet,int light){}
    private record Draw(double x0,double x1,double y,double z0,double z1,float u0,float u1,float v0,float v1,int color,int light){}
    private FluidStains(){}
    public static void clear(){MARKS.clear();world=null;sampleTick=Long.MIN_VALUE;rays=0;cursor=0;}
    private static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS);}
    private static void world(ClientLevel level){if(world!=level){clear();world=level;}if(sampleTick!=level.getGameTime()){sampleTick=level.getGameTime();rays=0;}}
    /** Returns the fitted ground height, or NaN when no safe dry surface was found. */
    public static double add(ClientLevel level,Vec3 origin,boolean coolant){
        if(!enabled())return Double.NaN;world(level);
        if(rays>=4||!level.hasChunkAt(BlockPos.containing(origin)))return Double.NaN;
        if(Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(origin)>48*48)return Double.NaN;
        rays++;
        var hit=level.clip(new ClipContext(origin,origin.add(0,-8,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,CollisionContext.empty()));
        if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=Direction.UP)return Double.NaN;
        var pos=hit.getBlockPos();if(!level.hasChunkAt(pos))return Double.NaN;
        var state=level.getBlockState(pos);if(!state.getFluidState().isEmpty())return Double.NaN;
        Vec3 p=hit.getLocation();
        for(var box:state.getCollisionShape(level,pos).toAabbs()){
            double top=pos.getY()+box.maxY;
            if(Math.abs(top-p.y)>.01||p.x<pos.getX()+box.minX||p.x>pos.getX()+box.maxX||p.z<pos.getZ()+box.minZ||p.z>pos.getZ()+box.maxZ)continue;
            long now=level.getGameTime();
            for(int i=0;i<MARKS.size();i++){
                var old=MARKS.get(i);
                if(old.pos.equals(pos)&&old.coolant==coolant&&Math.hypot(old.x-p.x,old.z-p.z)<.35){
                    MARKS.set(i,new Stain(pos.immutable(),state,old.x,top+.004,old.z,pos.getX()+box.minX,pos.getX()+box.maxX,
                            pos.getZ()+box.minZ,pos.getZ()+box.maxZ,FluidLeakLook.grow(old.radius),coolant,old.born,now,old.light));return top;
                }
            }
            if(MARKS.size()>=128)MARKS.removeFirst();
            MARKS.add(new Stain(pos.immutable(),state,p.x,top+.004,p.z,pos.getX()+box.minX,pos.getX()+box.maxX,
                    pos.getZ()+box.minZ,pos.getZ()+box.maxZ,.17F,coolant,now,now,LightCoordsUtil.getLightCoords(level,pos.above())));
            return top;
        }
        return Double.NaN;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.level!=world||!enabled()){clear();return;}if(world==null||mc.isPaused())return;
        world(world);long now=world.getGameTime();
        MARKS.removeIf(m->now<m.born||now-m.wet>FluidLeakLook.life(m.coolant));
        for(int i=0;i<8&&!MARKS.isEmpty();i++){
            int at=Math.floorMod(cursor++,MARKS.size());var mark=MARKS.get(at);
            if(!world.hasChunkAt(mark.pos)||world.getBlockState(mark.pos)!=mark.state)MARKS.remove(at);
        }
    }
    @SubscribeEvent public static void submit(SubmitCustomGeometryEvent event){
        if(!enabled()||MARKS.isEmpty()||world!=Minecraft.getInstance().level)return;
        var camera=event.getLevelRenderState().cameraRenderState.pos;
        long now=world.getGameTime();var draws=new ArrayList<Draw>();
        int cap=ClientConfig.get(ClientConfig.EFFECT_BUDGET)==ClientConfig.EffectBudget.LOW?32:96;
        for(var m:MARKS.reversed()){
            if(draws.size()>=cap)break;if(camera.distanceToSqr(m.x,m.y,m.z)>48*48)continue;
            double x0=Math.max(m.left,m.x-m.radius),x1=Math.min(m.right,m.x+m.radius),z0=Math.max(m.back,m.z-m.radius),z1=Math.min(m.front,m.z+m.radius);
            float a=FluidLeakLook.alpha(now-m.born,now-m.wet,FluidLeakLook.life(m.coolant));
            float offset=m.coolant?.5F:0;
            draws.add(new Draw(x0-camera.x,x1-camera.x,m.y-camera.y,z0-camera.z,z1-camera.z,
                    offset+(float)((x0-m.x)/m.radius+1)*.25F,offset+(float)((x1-m.x)/m.radius+1)*.25F,
                    (float)((z0-m.z)/m.radius+1)*.5F,(float)((z1-m.z)/m.radius+1)*.5F,
                    ((int)(255*a)<<24)|FluidLeakLook.tint(m.coolant),m.light));
        }
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(),RenderTypes.entityTranslucent(TEXTURE,false),(pose,buffer)->{
            for(var d:draws){
                double[] xs={d.x0,d.x0,d.x1,d.x1},zs={d.z0,d.z1,d.z1,d.z0};float[] us={d.u0,d.u0,d.u1,d.u1},vs={d.v0,d.v1,d.v1,d.v0};
                for(int i=0;i<4;i++)buffer.addVertex(pose,(float)xs[i],(float)d.y,(float)zs[i]).setColor(d.color).setUv(us[i],vs[i])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(d.light).setNormal(pose,0,1,0);
            }
        });
    }
    public static int count(){return MARKS.size();}
    public static int rays(){return rays;}
}
