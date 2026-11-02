package io.github.mcheliceport.shim;

import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Extra spawn data sent with an entity: NeoForge's {@code ComplexSpawn}. The entity
 * returns {@code SpawnData.addEntityPacket(this, ...)} from {@code getAddEntityPacket}, which
 * bundles the vanilla spawn packet with a payload carrying this data, so it works on both loaders.
 */
public interface ComplexSpawn {

    void writeSpawnData(RegistryFriendlyByteBuf buffer);

    void readSpawnData(RegistryFriendlyByteBuf buffer);
}
