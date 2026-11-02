package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponParam;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The empty weapon a seat without weapons holds; it never fires. Ported from upstream {@code MCH_WeaponDummy}. */
public class DummyWeapon extends WeaponBase {

    static final WeaponInfo DUMMY = new WeaponInfo(ContentLocation.builtin("none"), "none", "builtin");

    public DummyWeapon(Level w, Vec3 v, float yaw, float pitch, String nm, @Nullable WeaponInfo wi) {
        super(w, v, yaw, pitch, !nm.isEmpty() ? nm : "none", wi != null ? wi : DUMMY);
    }

    @Override
    public boolean shot(WeaponParam prm) {
        return false;
    }
}
