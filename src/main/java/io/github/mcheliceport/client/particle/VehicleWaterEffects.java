package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Detached, broken V foam and wheel spray. Fluid sampling and support maintenance are bounded. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class VehicleWaterEffects {
    private record Wake(Vec3 point,Vec3 forward,float width,BlockPos support,long born,int light){}
    private static final List<Wake> WAKES=new ArrayList<>();
    private static final Identifier TEXTURE=Identifier.withDefaultNamespace("textures/block/snow.png");
    private static ClientLevel world;private static long lastTick=Long.MIN_VALUE,processedTick=Long.MIN_VALUE;private static int reads,births,splashes,cursor,spray;
    private VehicleWaterEffects(){}
    private static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.VEHICLE_WATER_DETAILS);}
    public static void clear(){WAKES.clear();world=null;lastTick=processedTick=Long.MIN_VALUE;reads=births=splashes=cursor=spray=0;}
    private static void prepare(ClientLevel level){if(world!=level){clear();world=level;}if(lastTick!=level.getGameTime()){lastTick=level.getGameTime();reads=births=splashes=spray=0;}}
    private static double water(Vec3 point){
        var pos=BlockPos.containing(point);if(reads>=12||!world.hasChunkAt(pos))return Double.NaN;
        reads++;var fluid=world.getFluidState(pos);if(!fluid.is(FluidTags.WATER))return Double.NaN;
        for(int i=0;i<3;i++){
            if(reads>=12)return Double.NaN;reads++;var above=world.getFluidState(pos.above());
            if(!above.is(FluidTags.WATER))return pos.getY()+fluid.getHeight(world,pos);
            pos=pos.above();fluid=above;
        }
        return Double.NaN;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(world!=mc.level||!enabled()){clear();world=mc.level;}
        if(world==null||!enabled()||mc.player==null||mc.isPaused())return;
        prepare(world);if(processedTick==world.getGameTime())return;processedTick=world.getGameTime();
        long now=world.getGameTime();WAKES.removeIf(w->now<w.born||now-w.born>=40);
        // At most four independent support checks; drawing never reads the level.
        for(int i=0;i<4&&!WAKES.isEmpty();i++){
            int at=Math.floorMod(cursor++,WAKES.size());var wake=WAKES.get(at);
            if(!world.hasChunkAt(wake.support)||!world.getFluidState(wake.support).is(FluidTags.WATER))WAKES.remove(at);
        }
        int scanned=0;
        for(var entity:world.entitiesForRendering()){
            if(++scanned>128||births>=4||reads>=12)break;
            if(!(entity instanceof AircraftEntity ac)||ac.getAcInfo()==null||ac.isRemoved()||!VehicleWaterLook.moving(ac.getDeltaMovement().horizontalDistanceSqr())
                    ||mc.gameRenderer.mainCamera().position().distanceToSqr(ac.position())>48*48||Math.floorMod(now+ac.getId(),4*(1+EffectPerformance.pressure()))!=0)continue;
            double y=water(ac.position().add(0,-.1,0));if(!Double.isFinite(y)||Math.abs(y-ac.getY())>1.2)continue;
            var model=AircraftModels.peek(ac.getAcInfo());float width=VehicleWaterLook.width(model==null?ac.getBbWidth():model.body().maxX-model.body().minX);
            Vec3 direction=ac.getDeltaMovement().multiply(1,0,1).normalize(),side=new Vec3(-direction.z,0,direction.x);
            double rear=model==null?.6:Math.clamp(Math.abs(model.body().minZ)*.65,.4,3);
            Vec3 point=ac.position().add(direction.scale(-rear));point=new Vec3(point.x,y+.012,point.z);
            // Check the maximum widened footprint, so foam cannot expand onto a nearby bank.
            Vec3 end=point.add(direction.scale(-3.7));double a=water(end.add(side.scale(width+2.1)).add(0,-.1,0));
            double b=water(end.add(side.scale(-width-2.1)).add(0,-.1,0));
            if(!Double.isFinite(a)||!Double.isFinite(b)||Math.abs(a-y)>.1||Math.abs(b-y)>.1)continue;
            int cap=ClientConfig.get(ClientConfig.EFFECT_BUDGET)==ClientConfig.EffectBudget.LOW?24:64;
            while(WAKES.size()>=cap)WAKES.removeFirst();var support=BlockPos.containing(point.add(0,-.03,0));
            WAKES.add(new Wake(point,direction,width,support.immutable(),now,LightCoordsUtil.getLightCoords(world,support.above())));births++;
            for(int i=-1;i<=1;i+=2){
                Vec3 at=point.add(side.scale(width*i));if(!VehicleAtmosphere.admit(world,at,.06F))continue;
                mc.particleEngine.add(new SurfaceDebrisParticle(world,at.x,at.y,at.z,side.x*i*.05,.10,side.z*i*.05,.045F,.7F,.85F,.95F,Double.NEGATIVE_INFINITY,14,.02F));spray++;
            }
        }
    }
    /** Called by the existing swept debris collision, without adding a new collision query. */
    public static void splash(ClientLevel level,Vec3 point,double speed){
        if(!enabled()||Minecraft.getInstance().level!=level)return;prepare(level);
        if(splashes>=2||Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(point)>40*40)return;
        float radius=(float)Math.clamp(.35+speed*.3,.35,.9);if(!VehicleAtmosphere.admit(level,point,radius))return;
        splashes++;var engine=Minecraft.getInstance().particleEngine;
        engine.add(new RotorRippleParticle(level,point.x,point.y+.015,point.z,radius,.6F,0));
        for(int i=0;i<3;i++)if(VehicleAtmosphere.admit(level,point,.055F)){
            double angle=i*2.4;engine.add(new SurfaceDebrisParticle(level,point.x,point.y,point.z,Math.cos(angle)*.09,.14,Math.sin(angle)*.09,.05F,.75F,.9F,1,Double.NEGATIVE_INFINITY,16,.02F));
        }
    }
    @SubscribeEvent public static void submit(SubmitCustomGeometryEvent event){
        if(!enabled()||world!=Minecraft.getInstance().level||WAKES.isEmpty())return;
        var camera=event.getLevelRenderState().cameraRenderState.pos;float partial=Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long now=world.getGameTime();var snapshot=List.copyOf(WAKES);
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(),RenderTypes.entityTranslucent(TEXTURE,false),(pose,buffer)->{
            for(var w:snapshot){
                if(camera.distanceToSqr(w.point)>48*48)continue;float age=now-w.born+partial,a=VehicleWaterLook.alpha(age);if(a<=0)continue;
                Vec3 back=w.forward.scale(-1),side=new Vec3(-w.forward.z,0,w.forward.x);int color=((int)(255*a)<<24)|0xDCECF5;
                for(int sign:new int[]{-1,1})for(int n=0;n<4;n++){
                    double t0=n*.8,t1=t0+.52,width=w.width+age*.018;
                    Vec3 p0=w.point.add(back.scale(t0+age*.018)).add(side.scale(sign*(width+t0*.45)));
                    Vec3 p1=w.point.add(back.scale(t1+age*.018)).add(side.scale(sign*(width+t1*.45)));
                    Vec3 offset=side.scale(.045+.015*Math.sin(n*3+age*.2));Vec3[] points={p0.subtract(offset),p1.subtract(offset),p1.add(offset),p0.add(offset)};
                    for(int k=0;k<4;k++){var p=points[k].subtract(camera);buffer.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setColor(color).setUv(k<2?0:1,k%3==0?0:1)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(w.light).setNormal(pose,0,1,0);}
                }
            }
        });
    }
    public static int wakes(){return WAKES.size();}
    public static int reads(){return reads;}
    public static int spray(){return spray;}
}
