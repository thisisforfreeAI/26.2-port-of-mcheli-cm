package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.WeaponControlPayload;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.weapon.WeaponSet;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Integrated flight sequences beyond formula comparisons and the original VTOL takeoff check. */
final class PlaneFlightGameTests {
    private PlaneFlightGameTests() { }

    static void register() {
        ModGameTests.test("plane_conventional_takeoff_turn_land", 30, h -> flight(h, false));
        ModGameTests.test("plane_vtol_transition_turn_land", 30, h -> flight(h, true));
        ModGameTests.test("plane_weapons_both_authorities", 30, PlaneFlightGameTests::weapons);
    }

    private static PlaneEntity plane(GameTestHelper helper, String name, double height) {
        AircraftItem item = (AircraftItem) ModItems.contentItems().stream()
                .filter(i -> i.type() == ContentType.PLANE && i.contentName().equals(name))
                .findFirst().orElseThrow().item().get();
        Vec3 p = helper.absoluteVec(new Vec3(12.5, height, 12.5));
        PlaneEntity plane = (PlaneEntity) item.createAircraft(helper.getLevel(), p.x, p.y, p.z);
        helper.getLevel().addFreshEntity(plane);
        plane.setFuel(plane.getMaxFuel());
        return plane;
    }

    private static void tick(GameTestHelper helper, PlaneEntity plane) {
        // Scripted ticks can cross the normal GameTest arena; load the next collision chunk.
        helper.getLevel().getChunk(plane.blockPosition().getX() >> 4, plane.blockPosition().getZ() >> 4);
        plane.tick();
    }

    private static void flight(GameTestHelper helper, boolean vtol) {
        PlaneEntity plane = plane(helper, vtol ? "harrier_en" : "p-51d", 1);
        // The test framework encases each small arena in barriers. Fly outside those walls.
        plane.setPos(plane.getX() + 1000, plane.getY(), plane.getZ());
        Player pilot = GameTestSupport.player(helper, GameType.SURVIVAL, new Vec3(12.5, 1, 10));
        check(helper, pilot.startRiding(plane, true, true), "pilot mounted");
        if (plane.partCanopy != null) { plane.partCanopy.forceSwitch(false); }
        if (vtol) {
            plane.switchVtol(true);
            plane.partNozzle.rotation = plane.partNozzle.prevRotation = 90;
        }
        double startY = plane.getY();
        plane.setCurrentThrottle(1);
        plane.throttleUp = true;
        for (int t = 0; t < 100; t++) {
            if (!vtol) { plane.setPitch(-12); }
            tick(helper, plane);
        }
        check(helper, plane.getY() > startY + 3, "takes off and climbs: " + (plane.getY() - startY));
        if (vtol) {
            plane.switchVtol(false);
            for (int t = 0; t < 65; t++) { tick(helper, plane); }
            check(helper, plane.getVtolMode() == 0, "nozzles complete transition to forward flight");
        }
        plane.setPitch(5);
        plane.setOrientation(null);
        float yaw = plane.getYaw();
        for (int t = 0; t < 30; t++) {
            plane.setAngles(15, 0, 0.05F);
            tick(helper, plane);
        }
        check(helper, Math.abs(plane.getYaw() - yaw) > 3, "mouse input turns the aircraft: " + yaw + " -> " + plane.getYaw()
                + ", allowed=" + plane.canUpdateYaw() + ", free=" + plane.isFreeLookMode()
                + ", pos=" + plane.position() + ", count=" + plane.getCountOnUpdate()
                + ", ground=" + plane.blockWithin(3, -2) + ", hover=" + plane.isHovering()
                + ", vehicle=" + plane.getVehicle());
        check(helper, plane.getDeltaMovement().horizontalDistance() > 0.2, "forward flight after turn");
        plane.setRoll(0);
        plane.setPitch(0);
        plane.throttleUp = false;
        plane.setCurrentThrottle(0.6);
        boolean landed = false;
        for (int t = 0; t < 700; t++) {
            tick(helper, plane);
            if (plane.onGround()) { landed = true; break; }
        }
        check(helper, landed, "lands after approach");
        check(helper, !plane.isDestroyed() && plane.getDamageTaken() == 0
                && pilot.getHealth() == pilot.getMaxHealth(), "normal landing is safe");
        GameTestSupport.cleanUp(plane);
        helper.succeed();
    }

    @SuppressWarnings("removal")
    private static void weapons(GameTestHelper helper) {
        for (boolean serverAuthority : new boolean[] {false, true}) {
            PlaneEntity plane = plane(helper, "p-51d", 25);
            ServerPlayer pilot = helper.makeMockServerPlayerInLevel();
            try {
                check(helper, pilot.startRiding(plane, true, true), "pilot mounted");
                GameTestSupport.withConfig(ServerConfig.SERVER_AUTHORITATIVE_MOVEMENT, serverAuthority, () -> {
                    for (int t = 0; t < 25; t++) { tick(helper, plane); }
                    check(helper, plane.followsPilot() != serverAuthority, "selected movement authority");
                    WeaponSet ws = plane.getWeapon(0);
                    ws.setAmmo(ws.getMagSize());
                    ws.cooldown = 0;
                    int ammo = ws.getAmmo();
                    ModNetwork.applyWeaponControl(plane, pilot, WeaponControlPayload.none(plane.getId())
                            .withUseWeapon(0, 0, plane.getX(), plane.getY(), plane.getZ(), 0, 0));
                    check(helper, ws.getAmmo() == ammo - 1, "plane weapon consumes ammo in authority mode " + serverAuthority);
                    var bullets = helper.getLevel().getEntitiesOfClass(BaseBulletEntity.class,
                            plane.getBoundingBox().inflate(20), b -> b.shootingAircraft == plane);
                    check(helper, !bullets.isEmpty(), "plane projectile launched");
                    bullets.forEach(BaseBulletEntity::discard);
                });
            } finally {
                GameTestSupport.cleanUp(plane);
                helper.getLevel().getServer().getPlayerList().remove(pilot);
            }
        }
        helper.succeed();
    }
}
