package io.github.mcheliceport.shim.client.event;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** Collects the port's menu screens for the loader to register. */
public final class RegisterMenuScreensEvent extends ClientEvent {

    @FunctionalInterface
    public interface ScreenConstructor<M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> {
        U create(M menu, Inventory inventory, Component title);
    }

    public record Entry<M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>>(MenuType<? extends M> type,
            ScreenConstructor<M, U> constructor) {
    }

    public final List<Entry<?, ?>> screens = new ArrayList<>();

    public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M> type,
            ScreenConstructor<M, U> constructor) {
        screens.add(new Entry<>(type, constructor));
    }
}
