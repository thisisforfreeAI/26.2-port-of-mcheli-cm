package io.github.mcheliceport.shim;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/**
 * 1.21.1 stand-in for 26.2's {@code ValueInput}: the same reading methods over a
 * {@link CompoundTag}, so save code ported from 26.2 stays unchanged.
 */
public final class ValueInput {
    private final CompoundTag tag;
    private final HolderLookup.Provider lookup;

    private ValueInput(CompoundTag tag, HolderLookup.Provider lookup) {
        this.tag = tag;
        this.lookup = lookup;
    }

    public static ValueInput of(CompoundTag tag, HolderLookup.Provider lookup) {
        return new ValueInput(tag, lookup);
    }

    public CompoundTag tag() {
        return tag;
    }

    public HolderLookup.Provider lookup() {
        return lookup;
    }

    public <T> Optional<T> read(String key, Codec<T> codec) {
        Tag value = tag.get(key);
        if (value == null) {
            return Optional.empty();
        }
        return codec.parse(lookup.createSerializationContext(NbtOps.INSTANCE), value).result();
    }

    public Optional<ValueInput> child(String key) {
        return tag.contains(key, Tag.TAG_COMPOUND) ? Optional.of(new ValueInput(tag.getCompound(key), lookup))
                : Optional.empty();
    }

    public ValueInput childOrEmpty(String key) {
        return new ValueInput(tag.getCompound(key), lookup);
    }

    public boolean contains(String key) {
        return tag.contains(key);
    }

    public Optional<Integer> getInt(String key) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? Optional.of(tag.getInt(key)) : Optional.empty();
    }

    public Optional<Long> getLong(String key) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? Optional.of(tag.getLong(key)) : Optional.empty();
    }

    public Optional<String> getString(String key) {
        return tag.contains(key, Tag.TAG_STRING) ? Optional.of(tag.getString(key)) : Optional.empty();
    }

    public Optional<int[]> getIntArray(String key) {
        return tag.contains(key, Tag.TAG_INT_ARRAY) ? Optional.of(tag.getIntArray(key)) : Optional.empty();
    }

    public int getIntOr(String key, int fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback;
    }

    public long getLongOr(String key, long fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : fallback;
    }

    public byte getByteOr(String key, byte fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getByte(key) : fallback;
    }

    public short getShortOr(String key, short fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getShort(key) : fallback;
    }

    public float getFloatOr(String key, float fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getFloat(key) : fallback;
    }

    public double getDoubleOr(String key, double fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getDouble(key) : fallback;
    }

    public boolean getBooleanOr(String key, boolean fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getBoolean(key) : fallback;
    }

    public String getStringOr(String key, String fallback) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;
    }
}
