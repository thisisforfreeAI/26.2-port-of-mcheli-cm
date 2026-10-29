package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.CockpitLightMesh;
import io.github.mcheliceport.client.particle.EffectPerformance;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.damage.AirframeDamage;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Vector3f;

/** Authored, part-local lamps and a small original-texture cockpit overlay. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class FlightLighting {
    private static int lamps,cockpitVertices,lastLamps,lastCockpitVertices;private static Object world;
    private FlightLighting(){}
    public static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.NIGHT_FLIGHT_LIGHTING);}
    public static int lampsDrawn(){return lastLamps;}public static int cockpitVertices(){return lastCockpitVertices;}
    public static void clear(){lamps=cockpitVertices=lastLamps=lastCockpitVertices=0;world=null;LandingLightGround.clear();}
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event){
        var level=Minecraft.getInstance().level;if(world!=level){clear();world=level;}
        // 26.2 emits Pre after extraction: publish this frame before resetting its admission window.
        lastLamps=lamps;lastCockpitVertices=cockpitVertices;lamps=cockpitVertices=0;
    }
    private static boolean powered(AircraftEntity ac){return !ac.isDestroyed()&&ac.canUseFuel()&&ac.getCurrentThrottle()>.02;}
    public static boolean allowed(AircraftEntity ac,AircraftInfo.SearchLight light){
        return !light.m8Only||enabled()&&powered(ac)&&(!(ac instanceof PlaneEntity plane)||plane.partLandingGear!=null&&plane.partLandingGear.rotation<5);
    }
    private static boolean cockpit(AircraftEntity ac){
        var mc=Minecraft.getInstance();return mc.player!=null&&mc.options.getCameraType().isFirstPerson()&&ac.isPilot(mc.player)
                &&ac.getAcInfo().haveCanopy()&&ac.isCanopyClose()&&!ac.isUAV()&&!ac.isGunnerMode(mc.player)&&ac.getCameraId()==0&&!ac.isMissileCameraMode(mc.player);
    }
    public static void extract(AircraftEntity ac,AircraftRenderState render,float partial){
        if(!enabled()||render.thermal||ac.isDestroyed()||ac.getAcInfo()==null)return;
        if(ac.getAcInfo().flightLamps.isEmpty()&&render.searchLights.isEmpty())return;
        var mc=Minecraft.getInstance();float night=FlightLightLook.night(ac.level().getSkyDarken());
        var eye=mc.gameRenderer.mainCamera().position();double distance=eye.distanceTo(ac.position());
        int cap=FlightLightLook.lampLimit(ClientConfig.get(ClientConfig.EFFECT_BUDGET).ordinal(),EffectPerformance.pressure());
        // Terrain sampling consumes these already extracted beam poses; it never scans entities.
        for(int i=0;i<Math.min(4,render.searchLights.size());i++){
            var beam=render.searchLights.get(i);LandingLightGround.offer(ac,beam,partial);
            if(distance<96&&!render.far&&lamps<cap){
                Vec3 at=beam.point(0,0,0);float radius=FlightLightLook.radius(.06F,distance);
                render.flightLamps.add(new GlowGeometry.Lamp(at,radius,beam.light().colorStart&0xFFFFFF,beam.strength()));
                if(night>0)render.flightLamps.add(new GlowGeometry.Lamp(at,radius*3.2F,beam.light().colorStart&0xFFFFFF,.13F*night*beam.strength()));lamps++;
            }
        }
        if(render.far||render.texture==null||!powered(ac)||ac instanceof PlaneEntity plane&&AirframeDamage.missing(plane)!=0)return;
        if(distance>256)return;
        int index=0;
        for(var lamp:ac.getAcInfo().flightLamps){
            if(index++>=16)break;String name=lamp.part().startsWith("$")?lamp.part():"$"+lamp.part();
            for(var draw:render.draws){
                var parent=draw.groups().stream().filter(g->g.name().equalsIgnoreCase(name)).findFirst().orElse(null);if(parent==null)continue;
                if(lamp.kind()==AircraftInfo.FlightLampKind.COCKPIT){
                    if(night<=0||!cockpit(ac))break;var groups=CockpitLightMesh.get(parent,lamp);int size=groups.stream().mapToInt(g->g.vertexCount()).sum();
                    if(size==0||cockpitVertices+size>4096)break;
                    float health=ac.getMaxHP()>0?(float)ac.getHP()/ac.getMaxHP():1;
                    float backlight=io.github.mcheliceport.client.hud.CockpitFeedbackLook.backlight(ac.level().getGameTime(),ac.getId(),1-health,true);
                    int color=(Math.round(65*night*backlight)<<24)|lamp.color();
                    render.emissiveDraws.add(new AircraftRenderState.EmissiveDraw(render.texture,new org.joml.Matrix4f(draw.pose()),groups,color));cockpitVertices+=size;
                }else{
                    if(lamps>=cap)break;
                    float pulse=lamp.kind()==AircraftInfo.FlightLampKind.STROBE?FlightLightLook.strobe(ac.level().getGameTime(),ac.getId(),index):1;
                    if(pulse==0)break;
                    var p=lamp.position();var transformed=draw.pose().transformPosition((float)p.x,(float)p.y,(float)p.z,new Vector3f());
                    var at=new Vec3(transformed.x,transformed.y,transformed.z);float radius=FlightLightLook.radius(lamp.radius(),distance);
                    render.flightLamps.add(new GlowGeometry.Lamp(at,radius,lamp.color(),.95F));
                    if(night>0)render.flightLamps.add(new GlowGeometry.Lamp(at,radius*3.2F,lamp.color(),night*.13F));lamps++;
                }
                break;
            }
        }
    }
}
