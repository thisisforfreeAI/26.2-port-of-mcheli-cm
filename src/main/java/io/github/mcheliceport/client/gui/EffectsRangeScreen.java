package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.client.effect.EffectsRangeClient;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.effectrange.*;
import io.github.mcheliceport.net.EffectsRangePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** A small comparison panel; demonstrations run in the dedicated server-owned dimension. */
public final class EffectsRangeScreen extends Screen {
    private final Screen parent;private Button fire;
    public EffectsRangeScreen(Screen parent){super(Component.literal("MC Heli effects test range"));this.parent=parent;}
    private boolean inside(){return minecraft.level!=null&&EffectsRange.inRange(minecraft.level);}
    private static Component weapon(){return Component.literal("Weapon: "+RangeOptions.WEAPON_LABELS.get(EffectsRangeClient.options.weapon()));}
    private static Component surface(){return Component.literal("Surface: "+RangeOptions.SURFACES.get(EffectsRangeClient.options.surface()));}
    private static Component lighting(){return Component.literal("Lighting: "+RangeOptions.LIGHTING.get(EffectsRangeClient.options.lighting()));}
    private void configure(){if(inside())send(EffectsRange.CONFIGURE);}
    private void send(int action){var o=EffectsRangeClient.options;ClientPacketDistributor.sendToServer(new EffectsRangePayload(action,o.weapon(),o.surface(),o.lighting()));}
    private static void save(){if(ClientConfig.SPEC.isLoaded())ClientConfig.SPEC.save();}
    @Override protected void init(){
        int x=width/2-110,y=Math.max(38,height/2-82);
        addRenderableWidget(Button.builder(weapon(),b->{var o=EffectsRangeClient.options;EffectsRangeClient.options=new RangeOptions((o.weapon()+1)%4,o.surface(),o.lighting());configure();b.setMessage(weapon());}).bounds(x,y,220,20).build());
        addRenderableWidget(Button.builder(surface(),b->{var o=EffectsRangeClient.options;EffectsRangeClient.options=new RangeOptions(o.weapon(),(o.surface()+1)%4,o.lighting());configure();b.setMessage(surface());}).bounds(x,y+24,220,20).build());
        addRenderableWidget(Button.builder(lighting(),b->{var o=EffectsRangeClient.options;EffectsRangeClient.options=new RangeOptions(o.weapon(),o.surface(),(o.lighting()+1)%3);configure();b.setMessage(lighting());}).bounds(x,y+48,220,20).build());
        addRenderableWidget(Button.builder(budget(),b->{var all=ClientConfig.EffectBudget.values();ClientConfig.EFFECT_BUDGET.set(all[(ClientConfig.get(ClientConfig.EFFECT_BUDGET).ordinal()+1)%all.length]);save();b.setMessage(budget());}).bounds(x,y+72,108,20).build());
        addRenderableWidget(Button.builder(quality(),b->{boolean enhanced=EffectQuality.enhanced();ClientConfig.EFFECT_STYLE.set(enhanced?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);ClientConfig.EFFECT_QUALITY.set(enhanced?EffectQuality.CLASSIC:EffectQuality.ENHANCED);save();b.setMessage(quality());}).bounds(x+112,y+72,108,20).build());
        addRenderableWidget(Button.builder(modern(),b->{ClientConfig.MODERN_EXPLOSIONS.set(!ClientConfig.get(ClientConfig.MODERN_EXPLOSIONS));save();b.setMessage(modern());}).bounds(x,y+96,220,20).build());
        if(inside()){
            fire=addRenderableWidget(Button.builder(Component.literal("Fire demo"),b->{send(EffectsRange.FIRE);minecraft.gui.setScreen(null);}).bounds(x,y+124,108,20).build());
            addRenderableWidget(Button.builder(Component.literal("Reset pad"),b->{EffectsRangeClient.ready=false;send(EffectsRange.RESET);}).bounds(x+112,y+124,108,20).build());
            addRenderableWidget(Button.builder(Component.literal("Return"),b->{send(EffectsRange.LEAVE);minecraft.gui.setScreen(null);}).bounds(x,y+148,108,20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->minecraft.gui.setScreen(null)).bounds(x+112,y+148,108,20).build());
        }else{
            addRenderableWidget(Button.builder(Component.literal("Enter range"),b->{send(EffectsRange.ENTER);minecraft.gui.setScreen(null);}).bounds(x,y+124,220,20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(x,y+148,220,20).build());
        }
    }
    private static Component budget(){return Component.literal("Detail: "+ClientConfig.get(ClientConfig.EFFECT_BUDGET).name());}
    private static Component quality(){return Component.literal(EffectQuality.enhanced()?"Polished":"Classic");}
    private static Component modern(){return Component.literal("Explosions: "+(ClientConfig.get(ClientConfig.MODERN_EXPLOSIONS)?"Modern":"Current M8"));}
    @Override public void tick(){if(fire!=null){int cooldown=EffectsRangeClient.cooldown();fire.active=inside()&&EffectsRangeClient.ready&&!EffectsRangeClient.cooling();fire.setMessage(Component.literal(EffectsRangeClient.cooling()?(cooldown>0?"Cooling: "+((cooldown+19)/20)+" s":"Cooling..."):"Fire demo"));}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        super.extractRenderState(g,mx,my,partial);int y=Math.max(38,height/2-82);g.centeredText(font,title,width/2,y-24,0xFFFFFFFF);
        String line=inside()?(EffectsRangeClient.ready?"Ready · ":"Preparing pad · ")+minecraft.getFps()+" FPS now":"Singleplayer owner / operator only";
        g.centeredText(font,line,width/2,y+176,0xFFCCCCCC);
    }
}
