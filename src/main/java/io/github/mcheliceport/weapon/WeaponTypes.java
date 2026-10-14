package io.github.mcheliceport.weapon;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.weapon.types.AAMissileWeapon;
import io.github.mcheliceport.weapon.types.ASMissileWeapon;
import io.github.mcheliceport.weapon.types.ATMissileWeapon;
import io.github.mcheliceport.weapon.types.BombWeapon;
import io.github.mcheliceport.weapon.types.BuckshotWeapon;
import io.github.mcheliceport.weapon.types.CasWeapon;
import io.github.mcheliceport.weapon.types.DispenserWeapon;
import io.github.mcheliceport.weapon.types.DummyWeapon;
import io.github.mcheliceport.weapon.types.LaserWeapon;
import io.github.mcheliceport.weapon.types.MachineGun1Weapon;
import io.github.mcheliceport.weapon.types.MachineGun2Weapon;
import io.github.mcheliceport.weapon.types.MarkerRocketWeapon;
import io.github.mcheliceport.weapon.types.RailgunWeapon;
import io.github.mcheliceport.weapon.types.RocketWeapon;
import io.github.mcheliceport.weapon.types.SmokeWeapon;
import io.github.mcheliceport.weapon.types.TargetingPodWeapon;
import io.github.mcheliceport.weapon.types.TorpedoWeapon;
import io.github.mcheliceport.weapon.types.TvMissileWeapon;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The 18 weapon types by their content name. Ported from upstream {@code WeaponTypeRegistry}. */
public final class WeaponTypes {

    /** Creates a weapon of one type. */
    @FunctionalInterface
    public interface Factory {
        WeaponBase create(Level level, Vec3 pos, float yaw, float pitch, String name, WeaponInfo info);
    }

    private static final Map<String, Factory> TYPES = new LinkedHashMap<>();

    static {
        TYPES.put("machinegun1", MachineGun1Weapon::new);
        TYPES.put("machinegun2", MachineGun2Weapon::new);
        TYPES.put("buckshot", BuckshotWeapon::new);
        TYPES.put("tvmissile", TvMissileWeapon::new);
        TYPES.put("torpedo", TorpedoWeapon::new);
        TYPES.put("cas", CasWeapon::new);
        TYPES.put("rocket", RocketWeapon::new);
        TYPES.put("asmissile", ASMissileWeapon::new);
        TYPES.put("aamissile", AAMissileWeapon::new);
        TYPES.put("atmissile", ATMissileWeapon::new);
        TYPES.put("bomb", BombWeapon::new);
        TYPES.put("mkrocket", MarkerRocketWeapon::new);
        TYPES.put("dummy", DummyWeapon::new);
        TYPES.put("smoke", SmokeWeapon::new);
        TYPES.put("dispenser", DispenserWeapon::new);
        TYPES.put("targetingpod", TargetingPodWeapon::new);
        TYPES.put("railgun", RailgunWeapon::new);
        TYPES.put("laser", LaserWeapon::new);
    }

    private WeaponTypes() {
    }

    public static Factory get(String type) {
        return TYPES.get(type);
    }

    public static Map<String, Factory> all() {
        return Collections.unmodifiableMap(TYPES);
    }
}
