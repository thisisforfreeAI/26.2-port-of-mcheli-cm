package io.github.mcheliceport.fabricmixin;

import com.mojang.datafixers.DataFixer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The port's saved data has no data fixer, so its {@code SavedDataType}s pass a null
 * {@link DataFixTypes}, which NeoForge allows and vanilla does not: skip the update for those.
 */
@Mixin(SavedDataStorage.class)
abstract class SavedDataStorageMixin {

    @Redirect(method = "readTagFromDisk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/datafix/DataFixTypes;update(Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/nbt/CompoundTag;II)Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag mcheli$skipNullFixer(@Nullable DataFixTypes type, DataFixer fixer, CompoundTag tag,
            int fromVersion, int toVersion) {
        return type == null ? tag : type.update(fixer, tag, fromVersion, toVersion);
    }
}
