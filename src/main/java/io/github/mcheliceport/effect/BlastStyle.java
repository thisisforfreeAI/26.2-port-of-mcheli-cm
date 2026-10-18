package io.github.mcheliceport.effect;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Cosmetic weapon families, independent of damage, block destruction and server physics. */
public enum BlastStyle {
    BLAST, CANNON, HEAVY, INCENDIARY, PENETRATOR;

    public static BlastStyle forWeapon(@Nullable WeaponInfo weapon, boolean flaming) {
        if (flaming || weapon != null && (weapon.flaming || weapon.isFAE || weapon.fireContainer != null
                || weapon.payloadNTM == WeaponInfo.Payload.NTM_FIRE)) return INCENDIARY;
        if (weapon == null) return BLAST;
        return switch (weapon.type.toLowerCase(Locale.ROOT)) {
            case "machinegun1", "machinegun2", "railgun" -> CANNON;
            case "bomb", "cas" -> HEAVY;
            case "atmissile" -> PENETRATOR;
            default -> BLAST;
        };
    }
}
