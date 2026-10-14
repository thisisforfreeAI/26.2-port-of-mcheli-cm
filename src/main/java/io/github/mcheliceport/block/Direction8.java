package io.github.mcheliceport.block;

import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Eight horizontal directions, in steps of 45 degrees of yaw.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.helper.block.EnumDirection8}. The index order
 * (south = 0, then clockwise seen from above) and the names are upstream's, so the
 * {@code direction8} block state property keeps its values.
 */
public enum Direction8 implements StringRepresentable {
    SOUTH("south"),
    SOUTHWEST("southwest"),
    WEST("west"),
    NORTHWEST("northwest"),
    NORTH("north"),
    NORTHEAST("northeast"),
    EAST("east"),
    SOUTHEAST("southeast");

    private static final Direction8[] VALUES = values();
    private final String name;

    Direction8(String name) {
        this.name = name;
    }

    public static Direction8 byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }

    /** The direction nearest to a yaw in degrees (Minecraft's: 0 = south, 90 = west). */
    public static Direction8 fromYaw(double yaw) {
        return byIndex(Mth.floor(Mth.positiveModulo(yaw, 360.0) / 45.0 + 0.5));
    }

    public int index() {
        return this.ordinal();
    }

    /** Yaw in degrees, 0 to 315. */
    public float angle() {
        return this.ordinal() * 45f;
    }

    public Direction8 rotate(Rotation rotation) {
        return switch (rotation) {
            case NONE -> this;
            case CLOCKWISE_90 -> byIndex(this.ordinal() + 2);
            case CLOCKWISE_180 -> byIndex(this.ordinal() + 4);
            case COUNTERCLOCKWISE_90 -> byIndex(this.ordinal() + 6);
        };
    }

    public Direction8 mirror(Mirror mirror) {
        return switch (mirror) {
            case NONE -> this;
            // Flips north and south: yaw a becomes 180 - a.
            case LEFT_RIGHT -> byIndex(4 - this.ordinal());
            // Flips east and west: yaw a becomes -a.
            case FRONT_BACK -> byIndex(-this.ordinal());
        };
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
