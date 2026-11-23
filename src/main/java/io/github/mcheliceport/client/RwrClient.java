package io.github.mcheliceport.client;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.item.LightWeaponItem;
import io.github.mcheliceport.net.RwrLockPayload;
import io.github.mcheliceport.platform.Platform;
import io.github.mcheliceport.radar.RwrClientTracker;
import io.github.mcheliceport.radar.RwrThreat;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponClientHooks;
import io.github.mcheliceport.weapon.WeaponSet;
import io.github.mcheliceport.weapon.guidance.WeaponGuidanceSystem;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * The client's side of the radar warning receiver: reports the entity this player's seeker is
 * locking (upstream {@code PacketNotifyLock.send}), and plays the warnings for the vehicle the
 * player rides, as upstream's {@code updateRwr} and {@code CameraHandler} would have:
 * <ul>
 *   <li>{@code locked} every second while a seeker locks the vehicle (any vehicle, as upstream's
 *       lock notice);</li>
 *   <li>{@code alert} every second while a missile homes on it, for vehicles without flares (the
 *       missile detector already sounds it for those with);</li>
 *   <li>{@code pi} once when a new search radar appears on the scope (upstream's
 *       {@code alert_radar} was never in its assets).</li>
 * </ul>
 */
public final class RwrClient {

    private static final int REPORT_INTERVAL = 10;
    private static final int LOCK_TONE_INTERVAL = 20;
    private static final int LAUNCH_TONE_INTERVAL = 20;

    private static int ticks;
    private static int lastReportedTarget;
    private static int lastReportTick;
    private static int lastLockTone = -1000;
    private static int lastLaunchTone = -1000;
    private static final Set<Integer> KNOWN_SEARCH = new HashSet<>();

    private RwrClient() {
    }

    public static void tick() {
        ticks++;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        AircraftEntity ac = AircraftClientEvents.riddenAircraft(player);
        reportLock(player, ac);
        warn(ac);
    }

    private static void reportLock(LocalPlayer player, @Nullable AircraftEntity ac) {
        Entity target = lockingEntity(player, ac);
        int id = target != null ? target.getId() : 0;
        if (id != lastReportedTarget || id != 0 && ticks - lastReportTick >= REPORT_INTERVAL) {
            Platform.get().sendToServer(new RwrLockPayload(id));
            lastReportedTarget = id;
            lastReportTick = ticks;
        }
    }

    private static @Nullable Entity lockingEntity(LocalPlayer player, @Nullable AircraftEntity ac) {
        WeaponGuidanceSystem gs = null;
        if (ac != null) {
            WeaponSet ws = ac.getCurrentWeapon(player);
            WeaponBase weapon = ws != null ? ws.getCurrentWeapon() : null;
            gs = weapon != null ? weapon.getGuidanceSystem() : null;
        } else if (LightWeaponItem.isHeld(player)) {
            gs = io.github.mcheliceport.client.tool.LightWeaponClient.guidance();
        }
        return gs != null ? gs.getLockingEntity() : null;
    }

    private static void warn(@Nullable AircraftEntity ac) {
        if (ac == null || ac.isDestroyed()) {
            KNOWN_SEARCH.clear();
            return;
        }
        List<RwrThreat> threats = RwrClientTracker.threats(ac.getId());
        boolean locked = false;
        boolean launch = false;
        boolean newSearch = false;
        Set<Integer> search = new HashSet<>();
        for (RwrThreat t : threats) {
            if (t.isLaunch()) {
                launch = true;
            } else if (t.isLock()) {
                locked = true;
            } else {
                search.add(t.emitterId());
                newSearch |= !KNOWN_SEARCH.contains(t.emitterId());
            }
        }
        KNOWN_SEARCH.clear();
        KNOWN_SEARCH.addAll(search);
        if (locked && ticks - lastLockTone >= LOCK_TONE_INTERVAL) {
            lastLockTone = ticks;
            WeaponClientHooks.playSound("locked", 1.0F, 1.0F);
        }
        if (launch && !ac.haveFlare() && ticks - lastLaunchTone >= LAUNCH_TONE_INTERVAL) {
            lastLaunchTone = ticks;
            WeaponClientHooks.playSound("alert", 1.0F, 1.0F);
        }
        if (newSearch && !locked && !launch) {
            WeaponClientHooks.playSound("pi", 0.6F, 1.4F);
        }
    }

    public static void reset() {
        RwrClientTracker.clear();
        KNOWN_SEARCH.clear();
        lastReportedTarget = 0;
    }
}
