package io.github.mcheliceport.effect;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Cosmetic weapon families, independent of damage, block destruction and server physics. */
public enum BlastStyle {
    BLAST, CANNON, HEAVY, INCENDIARY, PENETRATOR;

    public static BlastStyle forWeapon(@Nullable WeaponInfo weapon, boolean flaming) {
        if (weapon == null) return flaming ? INCENDIARY : BLAST;
        // Igniting blocks is gameplay, not proof of an incendiary warhead. Stock HE Hellfires
        // and penetrators also set Flaming. Keep genuine fire/FAE payloads distinct.
        if (weapon.isFAE || weapon.fireContainer != null || weapon.payloadNTM == WeaponInfo.Payload.NTM_FIRE
                || weapon.name.equalsIgnoreCase("s-8dm") || weapon.name.equalsIgnoreCase("cbu-72")) return INCENDIARY;
        return switch (weapon.type.toLowerCase(Locale.ROOT)) {
            case "machinegun1", "machinegun2", "railgun" -> CANNON;
            case "bomb", "cas" -> HEAVY;
            case "atmissile" -> PENETRATOR;
            case "asmissile" -> weapon.piercing > 0 ? PENETRATOR : BLAST;
            case "rocket", "aamissile", "tvmissile", "torpedo" -> BLAST;
            default -> flaming || weapon.flaming ? INCENDIARY : BLAST;
        };
    }
}
