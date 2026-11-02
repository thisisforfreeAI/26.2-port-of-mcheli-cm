package io.github.mcheliceport.shim;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;

/**
 * Loader-neutral stand-in for NeoForge's {@code PartEntity}: a piece of a {@link MultipartEntity}
 * that is never saved or sent on its own. The shared {@code MultipartMixin} makes the level's entity
 * searches and id lookups see the parts, on both loaders.
 */
public abstract class PartEntity<T extends Entity> extends Entity {
    private final T parent;

    protected PartEntity(T parent) {
        super(parent.getType(), parent.level());
        this.parent = parent;
    }

    public T getParent() {
        return parent;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        throw new UnsupportedOperationException("Part entities are spawned with their parent");
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
