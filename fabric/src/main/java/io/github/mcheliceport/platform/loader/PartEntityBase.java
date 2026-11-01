package io.github.mcheliceport.platform.loader;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;

/**
 * A part of a multipart entity (a vehicle hit box), the Fabric twin of NeoForge's
 * {@code PartEntity}. It is never added to a level on its own: {@link SpawnDataEntity#partEntities()}
 * lists it, and the Fabric build's level mixins make entity lookups see it.
 */
public abstract class PartEntityBase<T extends Entity> extends Entity {
    private final T parent;

    protected PartEntityBase(T parent) {
        super(parent.getType(), parent.level());
        this.parent = parent;
    }

    public T getParent() {
        return this.parent;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        throw new UnsupportedOperationException();
    }
}
