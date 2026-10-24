package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.client.particle.ClientEffects;
import io.github.mcheliceport.client.particle.ModernExplosionLook;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.BlastStyle;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Disposable native UI/persistence checks; intentionally excluded from ordinary builds. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class ModernMenuProbe {
    private static int frame,guiScale;
    private static Screen pause;
    private static Object oldParticle,modernParticle;
    private static CompletableFuture<Void> reload;
    private static final KeyEvent ESC=new KeyEvent(256,0,0);
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    private static Button button(String prefix) {
        return Minecraft.getInstance().gui.screen().children().stream().filter(x->x instanceof Button)
                .map(x->(Button)x).filter(x->x.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow();
    }
    private static void click(String prefix) {
        var mc=Minecraft.getInstance();var b=button(prefix);
        var input=new MouseButtonEvent(b.getX()+b.getWidth()/2.0,b.getY()+b.getHeight()/2.0,new MouseButtonInfo(0,0));
        require(mc.gui.screen().mouseClicked(input,false),"Click not consumed: "+prefix);
        mc.gui.screen().mouseReleased(input);
    }
    private static void bounds() {
        var screen=Minecraft.getInstance().gui.screen();
        if(screen instanceof PauseScreen) require(screen.children().stream().filter(w->w instanceof Button b && b.getMessage().getString().startsWith("MC Heli Effects")).count()==1,"Duplicate/missing pause entry");
        var boxes=new java.util.ArrayList<EffectsMenuLayout.Rect>();
        for(var child:screen.children())if(child instanceof AbstractWidget w && w.visible) {
            var box=new EffectsMenuLayout.Rect(w.getX(),w.getY(),w.getWidth(),w.getHeight());
            require(box.x()>=0&&box.y()>=0&&box.x()+box.width()<=screen.width&&box.y()+box.height()<=screen.height,"Clipped menu control");
            require(boxes.stream().noneMatch(box::overlaps),"Overlapping menu controls");boxes.add(box);
        }
    }
    private static void capture(String name) {
        var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"m8-modern-ui-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,
                m->System.out.println("M8_MODERN_UI "+m.getString()));
    }
    private static boolean modern(Object particle) throws Exception {
        var field=particle.getClass().getDeclaredField("modern");field.setAccessible(true);return field.getBoolean(particle);
    }
    private static Object spawn(boolean expected) throws Exception {
        var mc=Minecraft.getInstance();var pos=mc.gameRenderer.mainCamera().position().add(0,0,12);
        ClientEffects.INSTANCE.explosionEffects(mc.level,pos.x,pos.y,pos.z,4,false,List.of(),BlastStyle.BLAST);
        var field=net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particlesToAdd");field.setAccessible(true);
        Object found=null;
        for(Object p:(java.util.Queue<?>)field.get(mc.particleEngine))
            if(p.getClass().getSimpleName().equals("BlastCloudParticle") && modern(p)==expected)found=p;
        require(found!=null,"Wrong/missing birth style: "+expected);return found;
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) throws Exception {
        if(!Boolean.getBoolean("mcheli.probeModernMenu"))return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.gui.overlay()!=null)return;
        if(reload!=null){if(!reload.isDone())return;reload.join();reload=null;}
        frame++;
        if(Integer.getInteger("mcheli.probeModernMenuStage",0)==1) {
            if(frame==20) {
                require(ClientConfig.get(ClientConfig.MODERN_EXPLOSIONS),"Modern preference did not persist across restart");
                io.github.mcheliceport.client.particle.ExplosionModes.select(ClientConfig.ExplosionMode.CURRENT);ClientConfig.SPEC.save();
                System.out.println("M8_MODERN_UI persisted_restart=true restored_off=true");mc.stop();
            }
            return;
        }
        switch(frame) {
            case 1 -> {
                guiScale=mc.options.guiScale().get();mc.getWindow().setWindowed(1920,1080);
                ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
                ClientConfig.DEFAULT_EXPLOSION_PARTICLE.set(false);io.github.mcheliceport.client.particle.ExplosionModes.select(ClientConfig.ExplosionMode.CURRENT);
                ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.EFFECT_TARGET_FPS.set(60);
                ClientConfig.ADAPTIVE_EFFECTS.set(true);
            }
            case 10 -> {pause=new PauseScreen(true);mc.gui.setScreen(pause);}
            case 14 -> {bounds();capture("pause");}
            case 18 -> {click("MC Heli Effects");require(mc.gui.screen() instanceof EffectsSettingsScreen,"Wrong menu screen");}
            case 22 -> {bounds();capture("off");oldParticle=spawn(false);}
            case 30 -> {click("Explosions:");require(ModernExplosionLook.enabled(),"Modern toggle did not activate");modernParticle=spawn(true);require(!modern(oldParticle),"Existing current particle changed art");}
            case 34 -> capture("on");
            case 36 -> {
                click("Detail:");require(ClientConfig.get(ClientConfig.EFFECT_BUDGET)==ClientConfig.EffectBudget.HIGH,"Detail did not change");
                click("Detail:");click("Detail:");
                click("Adaptive detail:");require(!ClientConfig.get(ClientConfig.ADAPTIVE_EFFECTS),"Adaptive control did not change");click("Adaptive detail:");
                for(int i=0;i<6;i++)click("Frame-rate goal:");
                require(ClientConfig.get(ClientConfig.EFFECT_TARGET_FPS)==60,"FPS cycle did not return to 60");
            }
            case 40 -> {click("Explosions:");click("Explosions:");require(!ModernExplosionLook.enabled(),"Modern toggle did not disable");require(modern(modernParticle),"Existing modern particle changed art");}
            case 44 -> click("Explosions:");
            case 50 -> {mc.gui.screen().keyPressed(ESC);require(mc.gui.screen()==pause,"ESC did not return to originating pause screen");}
            case 54 -> bounds();
            case 58 -> click("MC Heli Effects");
            case 62 -> {ClientConfig.EFFECT_QUALITY.set(EffectQuality.CLASSIC);mc.gui.setScreen(new EffectsSettingsScreen(pause));require(!ModernExplosionLook.enabled(),"Classic gate ignored");}
            case 66 -> {click("Explosions:");click("Explosions:");require(ClientConfig.get(ClientConfig.EFFECT_QUALITY)==EffectQuality.CLASSIC,"Toggle changed unrelated global quality");}
            case 70 -> capture("classic-inactive");
            case 74 -> click("Full mod settings");
            case 78 -> require(mc.gui.screen() instanceof net.neoforged.neoforge.client.gui.ConfigurationScreen,"Full config did not open");
            case 82 -> mc.gui.screen().onClose();
            case 86 -> {require(mc.gui.screen() instanceof EffectsSettingsScreen,"Full config did not return to effects");ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.SPEC.save();}
            case 90 -> {mc.gui.screen().onClose();mc.getWindow().setWindowed(960,720);mc.options.guiScale().set(3);mc.resizeGui();}
            case 94 -> {bounds();click("MC Heli Effects");}
            case 98 -> {bounds();require(mc.gui.screen().width<=480,"Small GUI not exercised");}
            case 102 -> capture("small");
            case 106 -> click("Done");
            case 110 -> {mc.gui.screen().onClose();mc.getWindow().setWindowed(1920,1080);mc.options.guiScale().set(guiScale);mc.resizeGui();}
            case 114 -> reload=mc.reloadResourcePacks();
            case 118 -> {
                var id=Identifier.fromNamespaceAndPath("mcheli_ce_port","m8_modern_blast_cloud");
                require(mc.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(id).contents().name().equals(id),"Modern atlas missing after reload");
                io.github.mcheliceport.client.particle.ExplosionModes.select(ClientConfig.ExplosionMode.MODERN);ClientConfig.SPEC.save();
            }
            case 122 -> {System.out.println("M8_MODERN_UI controls=true bounds=true birth_style=true classic_gate=true full_config=true reload=true saved_on=true");mc.stop();}
            default -> {}
        }
    }
}
