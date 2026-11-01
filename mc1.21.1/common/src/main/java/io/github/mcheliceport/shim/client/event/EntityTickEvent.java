package io.github.mcheliceport.shim.client.event;

import net.minecraft.world.entity.Entity;

/** An entity finished its tick (client and server); fed from the shared ENTITY_TICK_POST event. */
public abstract class EntityTickEvent extends ClientEvent {
    private final Entity entity;

    protected EntityTickEvent(Entity entity) {
        this.entity = entity;
    }

    public Entity getEntity() {
        return entity;
    }

    public static final class Post extends EntityTickEvent {
        public Post(Entity entity) {
            super(entity);
        }
    }
}
