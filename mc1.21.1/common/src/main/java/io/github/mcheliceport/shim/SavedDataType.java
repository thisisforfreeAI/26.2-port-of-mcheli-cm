package io.github.mcheliceport.shim;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 1.21.1 stand-in for 26.2's codec-based {@code SavedDataType}: keeps the data under {@code "data"}
 * in {@code data/<namespace>_<path>.dat} and builds the 1.21.1 {@link SavedData.Factory}.
 */
public record SavedDataType<T extends SavedData>(ResourceLocation id, Supplier<T> constructor, Codec<T> codec) {

    public String fileName() {
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }

    public SavedData.Factory<T> factory() {
        return new SavedData.Factory<>(constructor, this::load, null);
    }

    private T load(CompoundTag tag, HolderLookup.Provider registries) {
        return codec.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("data")).result()
                .orElseGet(constructor);
    }

    /** The body of the data's {@code save(CompoundTag, Provider)}. */
    public CompoundTag save(T data, CompoundTag tag, HolderLookup.Provider registries) {
        codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), data).result()
                .ifPresent(encoded -> tag.put("data", encoded));
        return tag;
    }
}
