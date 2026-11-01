package io.github.mcheliceport.shim.client.event;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** An entity's name tag is about to be drawn; {@link #setCanRender} with FALSE hides it. */
public final class RenderNameTagEvent extends ClientEvent {
    private final Entity entity;
    private final Component name;
    private Boolean canRender;

    public RenderNameTagEvent(Entity entity, Component name) {
        this.entity = entity;
        this.name = name;
    }

    public Entity getEntity() {
        return entity;
    }

    public Component getContent() {
        return name;
    }

    public void setCanRender(io.github.mcheliceport.shim.client.TriState state) {
        this.canRender = state == io.github.mcheliceport.shim.client.TriState.DEFAULT ? null : state == io.github.mcheliceport.shim.client.TriState.TRUE;
    }

    /** null: the default decides. */
    public Boolean canRender() {
        return canRender;
    }
}
