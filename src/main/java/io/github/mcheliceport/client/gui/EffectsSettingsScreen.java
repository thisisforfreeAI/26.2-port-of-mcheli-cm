package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.client.particle.ModernExplosionLook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/** Changes affect newly spawned effects; particles already in flight keep their birth art. */
public final class EffectsSettingsScreen extends Screen {
    private final Screen parent;
    public EffectsSettingsScreen(Screen parent) {super(Component.translatable("mcheli.effects.menu"));this.parent=parent;}
    @Override protected void init() {
        int x=width/2-110,y=Math.max(44,height/2-68);
        boolean available=EffectQuality.enhanced() && !ClientConfig.get(ClientConfig.DEFAULT_EXPLOSION_PARTICLE);
        var modern=addRenderableWidget(Button.builder(modernLabel(),b->{
            ClientConfig.MODERN_EXPLOSIONS.set(!ClientConfig.get(ClientConfig.MODERN_EXPLOSIONS));
            save();b.setMessage(modernLabel());
        }).bounds(x,y,220,20).build());
        modern.setTooltip(Tooltip.create(Component.translatable(available?"mcheli.effects.modern.tip":"mcheli.effects.modern.requires")));
        addRenderableWidget(Button.builder(detailLabel(),b->{
            var values=ClientConfig.EffectBudget.values();
            ClientConfig.EFFECT_BUDGET.set(values[(ClientConfig.get(ClientConfig.EFFECT_BUDGET).ordinal()+1)%values.length]);
            save();b.setMessage(detailLabel());
        }).bounds(x,y+24,220,20).build());
        addRenderableWidget(Button.builder(adaptiveLabel(),b->{
            ClientConfig.ADAPTIVE_EFFECTS.set(!ClientConfig.get(ClientConfig.ADAPTIVE_EFFECTS));save();b.setMessage(adaptiveLabel());
        }).bounds(x,y+48,220,20).build());
        addRenderableWidget(Button.builder(fpsLabel(),b->{
            int current=ClientConfig.get(ClientConfig.EFFECT_TARGET_FPS);int next=current<60?60:current<80?80:current<120?120:current<144?144:current<240?240:30;
            ClientConfig.EFFECT_TARGET_FPS.set(next);save();b.setMessage(fpsLabel());
        }).bounds(x,y+72,220,20).build());
        addRenderableWidget(Button.builder(Component.translatable("mcheli.effects.full"),b->{
            ModList.get().getModContainerById(McheliCePort.MOD_ID).ifPresent(container->
                    minecraft.gui.setScreen(new ConfigurationScreen(container,this)));
        }).bounds(x,y+102,220,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(x,y+126,220,20).build());
    }
    private static Component modernLabel() {
        return Component.translatable("mcheli.effects.modern",onOff(ClientConfig.get(ClientConfig.MODERN_EXPLOSIONS)));
    }
    private static Component detailLabel(){return Component.translatable("mcheli.effects.detail",Component.translatable("mcheli.effects.detail."+ClientConfig.get(ClientConfig.EFFECT_BUDGET).name().toLowerCase(java.util.Locale.ROOT)));}
    private static Component adaptiveLabel(){return Component.translatable("mcheli.effects.adaptive",onOff(ClientConfig.get(ClientConfig.ADAPTIVE_EFFECTS)));}
    private static Component fpsLabel(){return Component.translatable("mcheli.effects.fps",ClientConfig.get(ClientConfig.EFFECT_TARGET_FPS));}
    private static Component onOff(boolean on){return Component.translatable(on?"options.on":"options.off");}
    private static void save(){if(ClientConfig.SPEC.isLoaded())ClientConfig.SPEC.save();}
    @Override public void onClose(){save();minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return true;}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){
        super.extractRenderState(graphics,mouseX,mouseY,partial);
        graphics.centeredText(font,title,width/2,18,0xFFFFFFFF);
        if(ClientConfig.get(ClientConfig.MODERN_EXPLOSIONS) && !ModernExplosionLook.enabled())
            graphics.centeredText(font,Component.translatable("mcheli.effects.inactive"),width/2,32,0xFFFFCC66);
    }
}
