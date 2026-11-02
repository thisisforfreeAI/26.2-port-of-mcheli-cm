package io.github.mcheliceport.shim;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

/**
 * 1.21.1 stand-in for 26.2's {@code ValueOutput}: the same writing methods over a
 * {@link CompoundTag}, so save code ported from 26.2 stays unchanged.
 */
public final class ValueOutput {
    private final CompoundTag tag;
    private final HolderLookup.Provider lookup;

    private ValueOutput(CompoundTag tag, HolderLookup.Provider lookup) {
        this.tag = tag;
        this.lookup = lookup;
    }

    public static ValueOutput of(CompoundTag tag, HolderLookup.Provider lookup) {
        return new ValueOutput(tag, lookup);
    }

    public CompoundTag tag() {
        return tag;
    }

    public HolderLookup.Provider lookup() {
        return lookup;
    }

    public <T> void store(String key, Codec<T> codec, T value) {
        codec.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE), value).result()
                .ifPresent(encoded -> tag.put(key, encoded));
    }

    public <T> void storeNullable(String key, Codec<T> codec, T value) {
        if (value != null) {
            store(key, codec, value);
        }
    }

    public ValueOutput child(String key) {
        CompoundTag child = new CompoundTag();
        tag.put(key, child);
        return new ValueOutput(child, lookup);
    }

    public void discard(String key) {
        tag.remove(key);
    }

    public void putInt(String key, int value) {
        tag.putInt(key, value);
    }

    public void putLong(String key, long value) {
        tag.putLong(key, value);
    }

    public void putByte(String key, byte value) {
        tag.putByte(key, value);
    }

    public void putShort(String key, short value) {
        tag.putShort(key, value);
    }

    public void putFloat(String key, float value) {
        tag.putFloat(key, value);
    }

    public void putDouble(String key, double value) {
        tag.putDouble(key, value);
    }

    public void putBoolean(String key, boolean value) {
        tag.putBoolean(key, value);
    }

    public void putString(String key, String value) {
        tag.putString(key, value);
    }

    public void putIntArray(String key, int[] value) {
        tag.putIntArray(key, value);
    }
}
