package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.gui.EffectsSettingsScreen;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.BlastStyle;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Opt-in native renderer, menu, persistence and burst tests. Never included in release builds. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class NtmExplosionProbe {
    private static final boolean PRESENT=Boolean.getBoolean("mcheli.probeNtmExpectPresent");
    private static int frame,tick;
    private static long loadingSince,loadingLog;
    private static boolean running;
    private static String capture;
    private static Vec3 origin;
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void click(String prefix) {
        var mc=Minecraft.getInstance();
        var b=mc.gui.screen().children().stream().filter(w->w instanceof Button)
                .map(w->(Button)w).filter(w->w.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow();
        var event=new MouseButtonEvent(b.getX()+b.getWidth()/2.0,b.getY()+10,new MouseButtonInfo(0,0));
        require(mc.gui.screen().mouseClicked(event,false),"Click not consumed: "+prefix);
        mc.gui.screen().mouseReleased(event);
    }
    private static void bounds() {
        var screen=Minecraft.getInstance().gui.screen();
        var boxes=new java.util.ArrayList<io.github.mcheliceport.client.gui.EffectsMenuLayout.Rect>();
        for(var child:screen.children())if(child instanceof AbstractWidget w && w.visible) {
            var rect=new io.github.mcheliceport.client.gui.EffectsMenuLayout.Rect(w.getX(),w.getY(),w.getWidth(),w.getHeight());
            require(rect.x()>=0 && rect.y()>=0 && rect.x()+rect.width()<=screen.width && rect.y()+rect.height()<=screen.height,"Clipped control");
            require(boxes.stream().noneMatch(r->r.x()<rect.x()+rect.width() && r.x()+r.width()>rect.x() && r.y()<rect.y()+rect.height() && r.y()+r.height()>rect.y()),"Overlapping controls");boxes.add(rect);
        }
    }
    private static java.util.Queue<?> queued() throws Exception {
        var field=net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particlesToAdd");field.setAccessible(true);
        return (java.util.Queue<?>)field.get(Minecraft.getInstance().particleEngine);
    }
    private static int liveFaces() throws Exception {
        var field=NtmExplosionBridge.class.getDeclaredField("active");field.setAccessible(true);int sum=0;
        for(Object tracked:(java.util.Collection<?>)field.get(null)) {
            var method=tracked.getClass().getDeclaredMethod("faces");method.setAccessible(true);sum+=(Integer)method.invoke(tracked);
        }
        return sum;
    }
    private static void emit(float power,float footprint) throws Exception {
        var mc=Minecraft.getInstance();
        var blocks=new java.util.ArrayList<net.minecraft.core.BlockPos>();
        int radius=(int)footprint;
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)if(dx*dx+dz*dz<=radius*radius)
            blocks.add(net.minecraft.core.BlockPos.containing(origin.x+dx,origin.y,origin.z+dz));
        mc.particleEngine.clearParticles();
        ClientEffects.INSTANCE.explosionEffects(mc.level,origin.x,origin.y,origin.z,power,false,blocks,BlastStyle.HEAVY);
        String expected=PRESENT?(power<4?"com.hbm.particle.ParticleExplosionSmall":"com.hbm.particle.ParticleRocketFlame")
                :"io.github.mcheliceport.client.particle.BlastCloudParticle";
        boolean present=queued().stream().anyMatch(p->p.getClass().getName().equals(expected));
        int instanced=0;
        if(PRESENT && power>=4) {
            var field=Class.forName("com.hbm.client.particle.RocketFlameVisuals").getDeclaredField("ACTIVE");field.setAccessible(true);
            instanced=((java.util.List<?>)field.get(null)).size();
        }
        require(present || instanced>0,"Missing native/fallback class "+expected);
        if(PRESENT && power>=4) {
            if(!NtmExplosionLook.acceleratedBackend(mc.getWindow().backend().getName()))
                require(present && instanced==0,"Vulkan entered unsupported native instancing");
            System.out.println("M8_NTM accelerated_clouds="+instanced+" fallback_clouds="+present);
        }
        if(PRESENT && power>=4)require(queued().stream().anyMatch(p->p.getClass().getName().equals("com.hbm.particle.ParticleMukeWave")),"Missing native wave");
        require(!PRESENT || queued().stream().noneMatch(p->p.getClass().getSimpleName().equals("BlastCloudParticle")),"Mixed native and M8 clouds");
        System.out.println("M8_NTM power="+power+" footprint="+footprint+" class="+expected+" faces="+liveFaces());
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) throws Exception {
        if(!Boolean.getBoolean("mcheli.probeNtm"))return;
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null) {
            long now=System.currentTimeMillis();if(loadingSince==0)loadingSince=now;
            // Only the disposable GameTest fixture: vanilla asks before opening its experimental generator.
            if(mc.gui.screen() instanceof net.minecraft.client.gui.screens.BackupConfirmScreen) {
                click(net.minecraft.network.chat.Component.translatable("selectWorld.backupJoinSkipButton").getString());
                com.mojang.logging.LogUtils.getLogger().info("M8_NTM accepted disposable-world experimental-generator prompt");
            }
            if(now-loadingLog>10000) {
                loadingLog=now;com.mojang.logging.LogUtils.getLogger().info("M8_NTM loading screen={} overlay={}",
                        mc.gui.screen()==null?"none":mc.gui.screen().getClass().getName(),
                        mc.gui.overlay()==null?"none":mc.gui.overlay().getClass().getName());
            }
            require(now-loadingSince<180000,"Disposable NTM fixture did not open within 180 seconds");
            return;
        }
        if(mc.gui.overlay()!=null)return;
        if(Boolean.getBoolean("mcheli.probeNtmRestart")) {
            if(++frame==20) {
                require(ExplosionModes.selected()==ClientConfig.ExplosionMode.NTM_NEXT,"Mode did not survive restart");
                ExplosionModes.select(ClientConfig.ExplosionMode.CURRENT);ClientConfig.SPEC.save();
                System.out.println("M8_NTM persisted_restart=true restored_current=true");mc.stop();
            }
            return;
        }
        if(!running)switch(++frame) {
            case 1 -> {
                mc.getWindow().setWindowed(1920,1080);
                if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                var server=mc.getSingleplayerServer();
                require(server!=null,"Native probe requires its disposable integrated world");
                server.execute(()->{
                    var source=server.createCommandSourceStack();
                    server.getCommands().performPrefixedCommand(source,"gamemode spectator @a");
                    server.getCommands().performPrefixedCommand(source,"tp @a 0 -50 -128 0 12");
                    server.getCommands().performPrefixedCommand(source,"time set noon");
                    server.getCommands().performPrefixedCommand(source,"weather clear");
                });
                ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
                ClientConfig.DEFAULT_EXPLOSION_PARTICLE.set(false);ClientConfig.ADAPTIVE_EFFECTS.set(false);
                ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.EFFECT_TARGET_FPS.set(60);
                ExplosionModes.select(ClientConfig.ExplosionMode.CURRENT);
                mc.options.enableVsync().set(false);mc.options.framerateLimit().set(60);
                mc.options.particles().set(net.minecraft.server.level.ParticleStatus.ALL);
                require((NtmExplosionBridge.availability()==NtmExplosionBridge.Availability.AVAILABLE)==PRESENT,"Wrong native availability: "+NtmExplosionBridge.availability());
                System.out.println("M8_NTM availability="+NtmExplosionBridge.availability());
            }
            case 20 -> mc.gui.setScreen(new PauseScreen(true));
            case 24 -> {click("MC Heli Effects");bounds();}
            case 28 -> {click("Explosions:");require(ModernExplosionLook.enabled(),"Modern choice inactive");}
            case 32 -> {click("Explosions:");require(ExplosionModes.selected()==ClientConfig.ExplosionMode.NTM_NEXT,"Native choice inactive");require(!ModernExplosionLook.enabled(),"Native and Modern active together");}
            case 34 -> capture="menu";
            case 36 -> {click("Explosions:");require(ExplosionModes.selected()==ClientConfig.ExplosionMode.CURRENT,"Current choice inactive");click("Explosions:");click("Explosions:");}
            case 40 -> {ClientConfig.EFFECT_QUALITY.set(EffectQuality.CLASSIC);require(!ExplosionModes.customEnabled(),"Classic gate failed");ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);}
            case 44 -> {click("Done");require(mc.gui.screen() instanceof PauseScreen,"Done did not return to pause");}
            case 46 -> {mc.getWindow().setWindowed(960,720);mc.options.guiScale().set(3);mc.resizeGui();}
            case 48 -> {click("MC Heli Effects");bounds();require(mc.gui.screen().width==320,"Minimum GUI not exercised");require(ExplosionModes.selected()==ClientConfig.ExplosionMode.NTM_NEXT,"Reopening lost selection");}
            case 50 -> capture="menu-small";
            case 52 -> {mc.gui.screen().keyPressed(new KeyEvent(256,0,0));require(mc.gui.screen() instanceof PauseScreen,"ESC did not return to pause");}
            case 56 -> {mc.gui.screen().onClose();mc.getWindow().setWindowed(1920,1080);mc.options.guiScale().set(3);mc.resizeGui();
                origin=new Vec3(0,-59.5,-88);running=true;
                System.out.println("M8_NTM menu_cycle=true classic_gate=true done=true esc=true");}
            default -> {}
        }
        if(capture!=null) {
            String name=capture;capture=null;
            Screenshot.grab(mc.gameDirectory,"m8-ntm-"+(PRESENT?"native-":"missing-")+name+".png",mc.gameRenderer.mainRenderTarget(),1,
                    message->System.out.println("M8_NTM screenshot="+message.getString()));
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if(!Boolean.getBoolean("mcheli.probeNtm") || !running)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.isPaused())return;
        tick++;
        if(tick==1) {
            var camera=mc.gameRenderer.mainCamera().position();
            require(Math.abs(camera.x)<1 && Math.abs(camera.z+128)<1,"Probe camera did not leave the GameTest structures");
            emit(2,4);
        }
        if(tick==2||tick==15)capture="small-"+tick;
        if(!PRESENT && tick==35) {ClientConfig.SPEC.save();System.out.println("M8_NTM missing_fallback=true saved_native=true");mc.stop();return;}
        if(tick==40)emit(5,10);
        if(tick==41||tick==55||tick==80)capture="medium-"+tick;
        if(tick==140)emit(12,16);
        if(tick==141||tick==155||tick==180||tick==205)capture="large-"+tick;
        if(tick>=240 && tick<256)for(int i=0;i<128;i++)NtmExplosionBridge.emit(mc.level,origin.x,origin.y,origin.z,12,16);
        require(liveFaces()<=NtmExplosionLook.liveFaces(ClientConfig.EffectBudget.BALANCED),"Native live face cap exceeded");
        if(tick==360) {
            require(liveFaces()==0,"Native particle leases did not drain");
            require(NtmExplosionBridge.availability()==NtmExplosionBridge.Availability.AVAILABLE,"Native renderer failed during combat");
            ClientConfig.SPEC.save();System.out.println("M8_NTM native_small=true native_large=true native_wave=true burst_requests=2048 bounded=true drained=true saved_native=true");mc.stop();
        }
    }
}
