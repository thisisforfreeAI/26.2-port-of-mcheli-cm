package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.client.AircraftClientEvents;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.damage.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix3x2f;

/** One pilot's optional annunciator strip and sparse closed-canopy droplets. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class CockpitFeedbackLayer {
    public static final Identifier ID=Identifier.fromNamespaceAndPath("mcheli_ce_port","cockpit_feedback");
    private static final CockpitFeedbackLook LOOK=new CockpitFeedbackLook();
    private static AircraftEntity vehicle;
    private static boolean rain;
    private static long weatherAt=Long.MIN_VALUE;
    private static float hull=1,engine=1,fuel=1;
    private static int drawnDrops;
    private CockpitFeedbackLayer(){}
    public static void register(RegisterGuiLayersEvent event){event.registerAbove(AircraftHudLayer.ID,ID,CockpitFeedbackLayer::render);}
    private static boolean eligible(Minecraft mc,AircraftEntity ac){
        return ac!=null&&ac.getAcInfo()!=null&&new CockpitFeedbackLook.View(
                EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.COCKPIT_FEEDBACK),ac.isPilot(mc.player),
                mc.options.getCameraType().isFirstPerson(),ac.getAcInfo().haveCanopy(),ac.isCanopyClose(),
                ac.isUAV()||ac.isGunnerMode(mc.player)||ac.getCameraId()>0||ac.isMissileCameraMode(mc.player),ac.isDestroyed()).visible();
    }
    public static void clear(){vehicle=null;LOOK.reset();rain=false;weatherAt=Long.MIN_VALUE;drawnDrops=0;hull=engine=fuel=1;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();var ac=AircraftClientEvents.riddenAircraft(mc.player);
        if(mc.level==null||!eligible(mc,ac)){clear();return;}
        if(vehicle!=ac||ac.level()!=mc.level){clear();vehicle=ac;}
        long now=mc.level.getGameTime();
        if(weatherAt==Long.MIN_VALUE||now<weatherAt||now-weatherAt>=10){
            weatherAt=now;rain=mc.level.isRainingAt(BlockPos.containing(ac.position()).above(3));
        }
        LOOK.tick(now,rain);
        hull=CockpitFeedbackLook.health((float)ac.getHP()/Math.max(1,ac.getMaxHP()));
        fuel=ac.getMaxFuel()>0?CockpitFeedbackLook.health((float)ac.getFuel()/ac.getMaxFuel()):1;
        engine=1;for(var part:VehicleComponents.of(ac))if(part.kind()==ComponentKind.ENGINE)engine=Math.min(engine,part.health());
    }
    public static boolean active(){return vehicle!=null;}
    public static boolean active(AircraftEntity ac){return ac==vehicle&&eligible(Minecraft.getInstance(),ac);}
    public static float wetness(){return LOOK.wetness();}
    public static int drawnDrops(){return drawnDrops;}
    public static float damage(){return Math.max(1-hull,1-engine);}
    public static double instrumentThrottle(AircraftEntity ac){return ac.canUseFuel()?ac.getCurrentThrottle():0;}
    private static void render(GuiGraphicsExtractor g,DeltaTracker delta){
        drawnDrops=0;var mc=Minecraft.getInstance();var ac=vehicle;
        if(mc.level==null||mc.player==null||mc.gui.screen()!=null||mc.gui.hud.isHidden()||!eligible(mc,ac)
                ||AircraftClientEvents.riddenAircraft(mc.player)!=ac)return;
        float partial=delta.getGameTimeDeltaPartialTick(false);
        if(Math.abs(Mth.wrapDegrees(mc.player.getViewYRot(partial)-ac.getYaw()))>65
                ||Math.abs(mc.player.getViewXRot(partial)-ac.getPitch())>45)return;
        double time=mc.level.getGameTime()+partial;int seed=ac.getId();
        rain(g,time,seed,ac.getDeltaMovement().length());
        float damage=Math.max(1-hull,1-engine);double throttle=instrumentThrottle(ac);boolean powered=throttle>.05;
        float brightness=CockpitFeedbackLook.backlight(mc.level.getGameTime(),seed,damage,powered);
        var pose=g.pose();pose.pushMatrix();
        pose.translate(CockpitFeedbackLook.shift(time,seed,throttle,damage),CockpitFeedbackLook.shift(time*.83,seed+9,throttle,damage)*.6F);
        int x=8,y=g.guiHeight()-24;
        g.fill(x,y,x+112,y+19,0xB012191C);g.fill(x,y,x+112,y+1,0x805E6A69);
        // The frame and lamps move; the flight HUD, reticle and camera are untouched.
        lamp(g,x+4,y+4,"engine",engine<.5F?0xFFFF7759:0xFF87CBB2,engine<.5F,brightness);
        lamp(g,x+31,y+4,"fuel",fuel<.15F?0xFFFFC267:0xFF87CBB2,fuel<.15F,brightness);
        lamp(g,x+58,y+4,"hull",hull<.5F?0xFFFF7759:0xFF87CBB2,hull<.5F,brightness);
        lamp(g,x+85,y+4,"radar",!ac.isEntityRadarMounted()?0xFF657479:ac.getRadarBlankTicks()>0?0xFFFFC267:0xFF87CBB2,ac.getRadarBlankTicks()>0,brightness);
        pose.popMatrix();
    }
    private static void lamp(GuiGraphicsExtractor g,int x,int y,String key,int color,boolean warning,float brightness){
        int alpha=Math.round((warning?Math.max(.8F,brightness):brightness)*255);
        int tint=(color&0xFFFFFF)|(alpha<<24);
        g.fill(x,y,x+4,y+2,tint);g.text(Minecraft.getInstance().font,Component.translatable("mcheli_ce_port.cockpit."+key),x,y+4,tint,false);
    }
    private static void rain(GuiGraphicsExtractor g,double time,int seed,double speed){
        int count=CockpitFeedbackLook.drops(ClientConfig.get(ClientConfig.EFFECT_BUDGET).ordinal(),io.github.mcheliceport.client.particle.EffectPerformance.pressure(),LOOK.wetness());
        if(count==0)return;
        var dark=new GuiHudCanvas.QuadBuilder(false);var light=new GuiHudCanvas.QuadBuilder(false);
        for(int i=0;i<count;i++){
            var drop=CockpitFeedbackLook.drop(time,seed,i,speed);float size=1.4F*drop.size();if(size<.12F)continue;
            float x=drop.x()*g.guiWidth(),y=drop.y()*g.guiHeight(),tail=size*drop.tail();
            dark.add(new float[]{x,y-tail,x-size,y,x,y+size,x+size,y});
            light.add(new float[]{x-size*.6F,y,x-size*.15F,y-tail*.65F,x+size*.2F,y-tail*.3F,x-size*.2F,y+size*.2F});drawnDrops++;
        }
        submit(g,dark,0x33212F38);submit(g,light,0x527EB2C6);
    }
    private static void submit(GuiGraphicsExtractor g,GuiHudCanvas.QuadBuilder q,int color){
        float[] vertices=q.toArray();if(vertices.length==0)return;var pose=new Matrix3x2f(g.pose());var scissor=g.peekScissorStack();
        g.submitGuiElementRenderState(new HudShapeRenderState(RenderPipelines.GUI,TextureSetup.noTexture(),pose,vertices,false,color,scissor,HudShapeRenderState.bounds(vertices,2,pose,scissor)));
    }
}
