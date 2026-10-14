package io.github.mcheliceport.client.render;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftWeapons;
import io.github.mcheliceport.weapon.WeaponSet;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Reads the weapon angles {@link AircraftPartPoses} draws weapon parts and bays with, the way
 * upstream's {@code renderWeapon} and {@code renderWeaponBay} read them from the entity.
 *
 * <p>Upstream skipped counting a hidden {@code HideGM} part towards the index of the missile it
 * shows, so in first person the next missile of the same set showed or hid with the wrong shot;
 * here every part counts.
 */
public final class WeaponPartPoses {

    private WeaponPartPoses() {
    }

    public static AircraftPartPoses.WeaponPartPose[] weaponParts(AircraftEntity ac, float t,
            @Nullable Entity localPlayer, boolean firstPerson) {
        AircraftInfo info = ac.getAcInfo();
        if (info == null) {
            return new AircraftPartPoses.WeaponPartPose[0];
        }
        AircraftWeapons weapons = ac.weapons();
        Entity rider = ac.getPilot();
        AircraftPartPoses.WeaponPartPose[] poses = new AircraftPartPoses.WeaponPartPose[info.partWeapon.size()];
        WeaponSet lastWs = null;
        int weaponIndex = 0;
        float turretYaw = AircraftPartPoses.smooth(ac.lastRiderYaw - ac.getYaw(), ac.prevLastRiderYaw - ac.yRotO, t);
        for (int i = 0; i < poses.length; i++) {
            AircraftInfo.PartWeapon part = info.partWeapon.get(i);
            WeaponSet ws = weapons.getWeaponByName(part.name[0]);
            if (ws != lastWs) {
                weaponIndex = 0;
                lastWs = ws;
            }
            boolean hidden = false;
            if (part.hideGM && firstPerson && localPlayer != null) {
                if (ws == null) {
                    hidden = ac.isMountedEntity(localPlayer);
                } else {
                    for (String name : part.name) {
                        if (weapons.getWeaponUserByWeaponName(name) == localPlayer) {
                            hidden = true;
                            break;
                        }
                    }
                }
            }
            float yaw;
            float prevYaw;
            float pitch;
            float prevPitch;
            if (ws != null) {
                yaw = ws.getYaw() - ws.getDefYaw();
                prevYaw = ws.getPrevYaw() - ws.getDefYaw();
                pitch = ws.getPitch();
                prevPitch = ws.getPrevPitch();
            } else if (rider != null) {
                yaw = rider.getYRot() - ac.getYaw();
                prevYaw = rider.yRotO - ac.yRotO;
                pitch = rider.getXRot();
                prevPitch = rider.xRotO;
            } else {
                yaw = ac.lastRiderYaw - ac.getYaw();
                prevYaw = ac.prevLastRiderYaw - ac.yRotO;
                pitch = ac.lastRiderPitch;
                prevPitch = ac.prevLastRiderPitch;
            }
            if (yaw - prevYaw > 180.0F) {
                prevYaw += 360.0F;
            } else if (yaw - prevYaw < -180.0F) {
                prevYaw -= 360.0F;
            }
            float[] childRecoil = new float[part.child.size()];
            for (int c = 0; c < childRecoil.length; c++) {
                childRecoil[c] = recoil(weapons, ws, part.child.get(c).name, t);
            }
            boolean drawn = !part.isMissile || weapons.isWeaponOnCooldown(ws, weaponIndex);
            poses[i] = new AircraftPartPoses.WeaponPartPose(hidden, drawn, ws != null, turretYaw,
                    ws != null ? ws.getTurretYaw() : 0.0F, prevYaw + (yaw - prevYaw) * t,
                    prevPitch + (pitch - prevPitch) * t, ws != null ? ws.getDefYaw() : 0.0F,
                    recoil(weapons, ws, part.name, t),
                    ws != null ? ws.getPrevRotBarrel() + (ws.getRotBarrel() - ws.getPrevRotBarrel()) * t : 0.0F,
                    childRecoil);
            weaponIndex++;
        }
        return poses;
    }

    /** Upstream: the first weapon's recoil, or the largest among the part's linked sets. */
    private static float recoil(AircraftWeapons weapons, @Nullable WeaponSet ws, String[] names, float t) {
        if (ws == null || ws.recoilBuffer.length == 0) {
            return 0.0F;
        }
        WeaponSet.Recoil r = ws.recoilBuffer[0];
        if (names.length > 1) {
            for (String name : names) {
                WeaponSet linked = weapons.getWeaponByName(name);
                if (linked != null && linked.recoilBuffer.length > 0 && linked.recoilBuffer[0].recoilBuf > r.recoilBuf) {
                    r = linked.recoilBuffer[0];
                }
            }
        }
        return r.prevRecoilBuf + (r.recoilBuf - r.prevRecoilBuf) * t;
    }

    public static float[] weaponBays(AircraftEntity ac, float t) {
        AircraftWeapons.WeaponBay[] bays = ac.weapons().weaponBays;
        float[] rot = new float[bays.length];
        for (int i = 0; i < bays.length; i++) {
            rot[i] = bays[i].prevRot + (bays[i].rot - bays[i].prevRot) * t;
        }
        return rot;
    }
}
