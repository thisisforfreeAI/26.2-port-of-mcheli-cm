package io.github.mcheliceport.shim;

import net.minecraft.core.NonNullList;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

/** Small 26.2 to 1.21.1 adapters that do not deserve their own class. */
public final class Compat {

    private Compat() {
    }

    /** 26.2's {@code Item#use} returns a bare result; 1.21.1 wants the held stack with it. */
    public static InteractionResultHolder<ItemStack> holder(InteractionResult result, ItemStack stack) {
        return new InteractionResultHolder<>(result, stack);
    }

    /** 26.2's {@code ContainerHelper.loadAllItems(ValueInput, items)}. */
    public static void loadAllItems(ValueInput input, NonNullList<ItemStack> items) {
        net.minecraft.world.ContainerHelper.loadAllItems(input.tag(), items, input.lookup());
    }

    /** 26.2's {@code ContainerHelper.saveAllItems(ValueOutput, items)}. */
    public static void saveAllItems(ValueOutput output, NonNullList<ItemStack> items) {
        net.minecraft.world.ContainerHelper.saveAllItems(output.tag(), items, output.lookup());
    }

    public static void saveAllItems(ValueOutput output, NonNullList<ItemStack> items, boolean saveEmpty) {
        net.minecraft.world.ContainerHelper.saveAllItems(output.tag(), items, saveEmpty, output.lookup());
    }

    /** 26.2's {@code CompoundTag#getIntOr}. */
    public static int getIntOr(net.minecraft.nbt.CompoundTag tag, String key, int fallback) {
        return tag.contains(key, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback;
    }

    /** 26.2's {@code CompoundTag#getLongOr}. */
    public static long getLongOr(net.minecraft.nbt.CompoundTag tag, String key, long fallback) {
        return tag.contains(key, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : fallback;
    }

    /** 26.2's {@code CompoundTag#getFloatOr}. */
    public static float getFloatOr(net.minecraft.nbt.CompoundTag tag, String key, float fallback) {
        return tag.contains(key, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC) ? tag.getFloat(key) : fallback;
    }

    /** 26.2's {@code CompoundTag#getDoubleOr}. */
    public static double getDoubleOr(net.minecraft.nbt.CompoundTag tag, String key, double fallback) {
        return tag.contains(key, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC) ? tag.getDouble(key) : fallback;
    }

    /** 26.2's {@code CompoundTag#getBooleanOr}. */
    public static boolean getBooleanOr(net.minecraft.nbt.CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC) ? tag.getBoolean(key) : fallback;
    }

    /** 26.2's {@code CompoundTag#getStringOr}. */
    public static String getStringOr(net.minecraft.nbt.CompoundTag tag, String key, String fallback) {
        return tag.contains(key, net.minecraft.nbt.Tag.TAG_STRING) ? tag.getString(key) : fallback;
    }
}
