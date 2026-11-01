package io.github.mcheliceport.content.weapon;

/**
 * Spent cartridge a weapon ejects. Ported from upstream {@code com.norwood.mcheli.weapon.MCH_Cartridge},
 * without the model reference (the client loads the model by {@link #name}).
 */
public class CartridgeInfo {

    public final String name;
    public final float acceleration;
    public final float yaw;
    public final float pitch;
    public final float bound;
    public final float gravity;
    public final float scale;

    public CartridgeInfo(String nm, float a, float y, float p, float b, float g, float s) {
        this.name = nm;
        this.acceleration = a;
        this.yaw = y;
        this.pitch = p;
        this.bound = b;
        this.gravity = g;
        this.scale = s;
    }
}
