package io.github.mcheliceport.client.effect;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.effectrange.*;
import io.github.mcheliceport.net.EffectsRangeNoticePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Scoped reset and actual displayed FPS; particle counts are never presented as frame rate. */
public final class EffectsRangeClient {
    public static RangeOptions options=new RangeOptions(0,0,0);public static boolean ready;private static final RangeReadiness READINESS=new RangeReadiness();
    public static boolean cooling(){return READINESS.cooling();}
    public static int cooldown(){var level=Minecraft.getInstance().level;return level==null?0:READINESS.countdown(level.getGameTime());}
    private EffectsRangeClient(){}
    public static void register(){EffectsRangeClientHooks.register(EffectsRangeClient::notice);}
    private static void notice(EffectsRangeNoticePayload p){
        var mc=Minecraft.getInstance();if(mc.level==null||!EffectsRange.inRange(mc.level)||p.state()<0||p.state()>4||!RangeOptions.valid(p.weapon(),p.surface(),p.lighting()))return;
        options=new RangeOptions(p.weapon(),p.surface(),p.lighting());READINESS.notice(p.state(),p.cooldown(),mc.level.getGameTime());ready=READINESS.ready();
        if(p.state()==0||p.state()==1){mc.particleEngine.clearParticles();BlastMarks.clear();io.github.mcheliceport.client.weapon.WeaponEffects.clear();}
    }
    public static void registerGui(RegisterGuiLayersEvent event){
        event.registerAboveAll(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID,"effects_range"),(g,delta)->{
            var mc=Minecraft.getInstance();if(mc.level==null||!EffectsRange.inRange(mc.level)||mc.gui.hud.isHidden()||mc.gui.screen()!=null)return;
            String line="Test range · "+RangeOptions.WEAPON_LABELS.get(options.weapon())+" · "+mc.getFps()+" FPS";
            g.fill(3,3,mc.font.width(line)+7,16,0x99000000);g.text(mc.font,line,5,5,0xFFE0E0E0);
            g.text(mc.font,ready?"ESC > MC Heli Effects > Test range":"Preparing target pad...",5,18,0xFFC7C7C7);
        });
    }
}
