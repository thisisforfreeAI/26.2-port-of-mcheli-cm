package io.github.mcheliceport.client.render;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/**
 * Non-shipping scene for long-range vehicles: render distance 4 (vanilla far plane 256 blocks), planes
 * at 400, 1,000 and 2,000 blocks seen through a narrow field of view, captured with
 * {@code render.farVehicleDistance} at 0 (vanilla) and at its default.
 * Run with {@code -Dmcheli.probeFarVehicles=true -I scripts/visual-probe.init.gradle runClient}.
 */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class FarVehicleProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeFarVehicles");
    private static final int[] RANGES={400,1000,2000};
    private static final int[] LIMITS={0,3000};
    private static int ticks;private static boolean created;private static String capture;private static Vec3 eye;
    private static final PlaneEntity[] PLANES=new PlaneEntity[RANGES.length];
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){xo=getX();yo=getY();zo=getZ();yRotO=getYRot();xRotO=getXRot();}
    }
    @SubscribeEvent public static void screen(ScreenEvent.Init.Post event){
        if(!ENABLED||created||Minecraft.getInstance().level!=null)return;
        if(!(event.getScreen() instanceof TitleScreen||event.getScreen() instanceof DisconnectedScreen))return;
        var mc=Minecraft.getInstance();created=true;
        if(mc.getLevelSource().levelExists("M6Smoke")){mc.createWorldOpenFlows().openWorld("M6Smoke",()->mc.gui.setScreen(new TitleScreen()));return;}
        var settings=new LevelSettings("M6Smoke",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("M6Smoke",settings,new WorldOptions(20261028L,false,false),WorldPresets::createTestWorldDimensions,new TitleScreen());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        ticks++;
        if(ticks==20){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false","tp @s 0 200 0 0 0"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.renderDistance().set(4);mc.options.cloudRange().set(2);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
        }
        if(ticks==60){
            eye=new Vec3(0,200,0);
            for(int i=0;i<RANGES.length;i++){
                var p=new Plane(mc);p.setId(976000+i);p.setAircraftTypeName("f22a");p.setTextureName("f22a");
                // Side by side across the view, each farther and larger in the frame by the same angle.
                double side=(i-1)*RANGES[i]*0.02;p.setPos(side,200+RANGES[i]*0.004,RANGES[i]);p.setYRot(90);p.yRotO=90;
                var parts=p.getParts();for(int k=0;k<parts.length;k++)parts[k].setId(986000+i*100+k);
                mc.level.addEntity(p);PLANES[i]=p;
            }
        }
        for(int l=0;l<LIMITS.length;l++){
            if(ticks==100+l*80)ClientConfig.FAR_VEHICLE_DISTANCE.set(LIMITS[l]);
            if(ticks==160+l*80){capture="limit-"+LIMITS[l];
                System.out.println("FAR_PROBE limit="+LIMITS[l]+" depthFar="+FarVehicles.depthFar(256F)+" entities="+mc.level.getEntityCount());}
        }
        if(ticks==300){System.out.println("FAR_PROBE done");mc.stop();}
    }
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov event){if(ENABLED&&eye!=null){event.getCamera().setPosition(eye);event.setFOV(6);}}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event){if(ENABLED&&eye!=null){event.setYaw(0);event.setPitch(-0.6F);event.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null)return;var mc=Minecraft.getInstance();String name=capture;capture=null;
        Screenshot.grab(mc.gameDirectory,"far-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("FAR_PROBE "+m.getString()));
    }
}
