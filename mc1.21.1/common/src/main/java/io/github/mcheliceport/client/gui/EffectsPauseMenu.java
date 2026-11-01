package io.github.mcheliceport.client.gui;

import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import io.github.mcheliceport.shim.client.event.SubscribeEvent;
import io.github.mcheliceport.shim.client.event.ScreenEvent;

@io.github.mcheliceport.shim.client.event.ClientSubscriber
public final class EffectsPauseMenu {
    private EffectsPauseMenu() {}
    @SubscribeEvent public static void init(ScreenEvent.Init.Post event) {
        if(!(event.getScreen() instanceof PauseScreen screen) || !screen.showsPauseMenu())return;
        var occupied=new ArrayList<EffectsMenuLayout.Rect>();
        for(var listener:event.getListenersList()) if(listener instanceof AbstractWidget w && w.visible)
            occupied.add(new EffectsMenuLayout.Rect(w.getX(),w.getY(),w.getWidth(),w.getHeight()));
        var place=EffectsMenuLayout.entry(screen.width,screen.height,occupied);
        if(place==null)return;
        event.addListener(Button.builder(Component.translatable("mcheli.effects.menu"),
                b->Minecraft.getInstance().setScreen(new EffectsSettingsScreen(screen)))
                .bounds(place.x(),place.y(),place.width(),place.height()).build());
    }
}
