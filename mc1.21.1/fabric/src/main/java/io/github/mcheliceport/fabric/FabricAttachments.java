package io.github.mcheliceport.fabric;

import io.github.mcheliceport.McheliCePort;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** NeoForge's {@code Entity#getPersistentData} as a saved Fabric attachment that survives death. */
final class FabricAttachments {

    private static final AttachmentType<CompoundTag> PERSISTENT_DATA = AttachmentRegistry.<CompoundTag>builder()
            .persistent(CompoundTag.CODEC)
            .initializer(CompoundTag::new)
            .copyOnDeath()
            .buildAndRegister(ResourceLocation.fromNamespaceAndPath(McheliCePort.MOD_ID, "persistent_data"));

    private FabricAttachments() {
    }

    static void init() {
    }

    static CompoundTag persistentData(Entity entity) {
        return entity.getAttachedOrCreate(PERSISTENT_DATA);
    }
}
