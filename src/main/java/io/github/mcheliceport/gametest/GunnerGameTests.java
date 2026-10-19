package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.flare.FlareEntity;
import io.github.mcheliceport.entity.gunner.GunnerEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.entity.weapon.ATMissileEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.item.SpawnGunnerItem;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.weapon.WeaponSet;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** Upstream's NPC gunners ({@code MCH_EntityGunner} and {@code MCH_ItemSpawnGunner}). */
final class GunnerGameTests {

    private GunnerGameTests() {
    }

    static void register() {
        ModGameTests.test("gunner_fires_at_monster", 200, GunnerGameTests::firesAtMonster);
        ModGameTests.test("gunner_holds_fire_when_switched_off", 80, GunnerGameTests::holdsFire);
        ModGameTests.test("gunner_vs_player_needs_other_team", 20, GunnerGameTests::vsPlayer);
        ModGameTests.test("gunner_fires_flares_at_missile", 60, GunnerGameTests::flares);
        ModGameTests.test("gunner_spawn_item_seats_and_removes", 40, GunnerGameTests::spawnItem);
        ModGameTests.test("gunner_off_vehicle_goes_away", 60, GunnerGameTests::offVehicle);
    }

    private static GunnerEntity gunner(GameTestHelper h, int targetType) {
        GunnerEntity gunner = new GunnerEntity(ModEntityTypes.GUNNER.get(), h.getLevel());
        gunner.targetType = targetType;
        return gunner;
    }

    /** A gunner of the given type in the AH-64's second seat, which works the M230. */
    static GunnerEntity seatGunner(GameTestHelper h, HeliEntity heli, int targetType) {
        SeatEntity seat = heli.getSeats()[0];
        GunnerEntity gunner = gunner(h, targetType);
        gunner.snapTo(seat.getX(), seat.getY(), seat.getZ(), 0.0F, 0.0F);
        h.getLevel().addFreshEntity(gunner);
        check(h, gunner.startRiding(seat), "the gunner took the second seat");
        return gunner;
    }

    private static Husk husk(GameTestHelper h, Vec3 pos) {
        Husk husk = EntityTypes.HUSK.create(h.getLevel(), EntitySpawnReason.COMMAND);
        husk.setNoAi(true);
        husk.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        h.getLevel().addFreshEntity(husk);
        return husk;
    }

    static boolean bulletsNear(GameTestHelper h, AircraftEntity heli) {
        return !h.getLevel().getEntitiesOfClass(BaseBulletEntity.class, heli.getBoundingBox().inflate(40.0)).isEmpty();
    }

    static void loadM230(GameTestHelper h, HeliEntity heli, GunnerEntity gunner) {
        WeaponSet ws = heli.getCurrentWeapon(gunner);
        check(h, ws != null && ws.isName("m230"), "the gunner has the M230");
        ws.setAmmo(ws.getMagSize());
        ws.setReserveAmmo(ws.getInfo().maxAmmo);
    }

    /** A gunner turns towards a monster in front and fires the seat's gun at it. */
    private static void firesAtMonster(GameTestHelper h) {
        HeliEntity heli = GameTestSupport.spawn(h, AH64, new Vec3(8.5, 1, 4.5), 0.0F);
        h.runAfterDelay(2, () -> {
            GunnerEntity gunner = seatGunner(h, heli, GunnerEntity.VS_MONSTER);
            check(h, heli.getGunnerStatus(), "the gunner switch starts on");
            Husk husk = husk(h, heli.position().add(0.0, 0.0, 16.0));
            h.runAfterDelay(2, () -> loadM230(h, heli, gunner));
            h.succeedWhen(() -> {
                check(h, gunner.getTargetEntity() == husk, "the gunner has not picked the husk");
                check(h, bulletsNear(h, heli) || husk.getHealth() < husk.getMaxHealth(), "the gunner has not fired");
                gunner.stopRiding();
                gunner.discard();
                husk.discard();
                GameTestSupport.cleanUp(heli);
            });
        });
    }

    /** With the vehicle's gunner switch off, the gunner does not fire. */
    private static void holdsFire(GameTestHelper h) {
        HeliEntity heli = GameTestSupport.spawn(h, AH64, new Vec3(8.5, 1, 4.5), 0.0F);
        h.runAfterDelay(2, () -> {
            heli.setGunnerStatus(false);
            GunnerEntity gunner = seatGunner(h, heli, GunnerEntity.VS_MONSTER);
            Husk husk = husk(h, heli.position().add(0.0, 0.0, 16.0));
            h.runAfterDelay(2, () -> loadM230(h, heli, gunner));
            h.runAfterDelay(60, () -> {
                check(h, gunner.getTargetEntity() == null, "a switched-off gunner picked a target");
                check(h, !bulletsNear(h, heli), "a switched-off gunner fired");
                gunner.stopRiding();
                gunner.discard();
                husk.discard();
                GameTestSupport.cleanUp(heli);
                h.succeed();
            });
        });
    }

    /**
     * A gunner vs player attacks only players of another team, and needs a team itself; it never
     * attacks creative players.
     */
    private static void vsPlayer(GameTestHelper h) {
        HeliEntity heli = GameTestSupport.spawn(h, AH64, new Vec3(8.5, 1, 4.5), 0.0F);
        h.runAfterDelay(2, () -> {
            GunnerEntity gunner = seatGunner(h, heli, GunnerEntity.VS_PLAYER);
            WeaponSet ws = heli.getCurrentWeapon(gunner);
            Player player = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(8.5, 1, 14.5));
            Player creative = GameTestSupport.player(h, GameType.CREATIVE, new Vec3(9.5, 1, 14.5));
            Scoreboard scoreboard = h.getLevel().getScoreboard();
            PlayerTeam red = scoreboard.addPlayerTeam("gunner_red");
            PlayerTeam blue = scoreboard.addPlayerTeam("gunner_blue");
            try {
                check(h, !gunner.canAttackEntity(player, heli, ws), "a gunner with no team attacks a player");
                gunner.setTeamName(red.getName());
                check(h, gunner.canAttackEntity(player, heli, ws), "a team gunner skips a player in no team");
                scoreboard.addPlayerToTeam(player.getScoreboardName(), red);
                check(h, !gunner.canAttackEntity(player, heli, ws), "a gunner attacks its own team");
                scoreboard.addPlayerToTeam(player.getScoreboardName(), blue);
                check(h, gunner.canAttackEntity(player, heli, ws), "a gunner skips the other team");
                check(h, !gunner.canAttackEntity(creative, heli, ws), "a gunner attacks a creative player");
                check(h, gunner.getDisplayName().getString().equals("gunner_red Gunner"),
                        "named " + gunner.getDisplayName().getString());
            } finally {
                scoreboard.removePlayerTeam(red);
                scoreboard.removePlayerTeam(blue);
            }
            gunner.stopRiding();
            gunner.discard();
            GameTestSupport.cleanUp(heli);
            h.succeed();
        });
    }

    /** A gunner fires the vehicle's flares when a guided missile homes on it. */
    private static void flares(GameTestHelper h) {
        HeliEntity heli = GameTestSupport.spawn(h, AH64, new Vec3(8.5, 1, 4.5), 0.0F);
        h.runAfterDelay(2, () -> {
            check(h, heli.haveFlare() && heli.canUseFlare(), "the AH-64 has flares ready");
            GunnerEntity gunner = seatGunner(h, heli, GunnerEntity.VS_MONSTER);
            Vec3 from = heli.position().add(0.0, 120.0, 0.0);
            // An anti-tank missile, which (unlike a heat seeker) keeps homing on a vehicle on the ground.
            ATMissileEntity missile = new ATMissileEntity(h.getLevel(), from.x, from.y, from.z, 0.0, 0.0, 0.0, 0.0F,
                    90.0F, 0.0F);
            missile.setName("9k114");
            // A missile without a shooter is not valid and goes away.
            missile.shootingEntity = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(8.5, 1, 40.5));
            h.getLevel().addFreshEntity(missile);
            missile.setTargetEntity(heli);
            h.succeedWhen(() -> {
                check(h, !heli.canUseFlare(), "the gunner has not fired flares");
                check(h, !h.getLevel().getEntitiesOfClass(FlareEntity.class, heli.getBoundingBox().inflate(16.0))
                        .isEmpty(), "no flares out");
                missile.discard();
                gunner.stopRiding();
                gunner.discard();
                GameTestSupport.cleanUp(heli);
            });
        });
    }

    /**
     * The spawn item seats a gunner of the player's team in the seat the player looks at; used again
     * on the gunner, it takes the gunner off. A creative player keeps the item, and the gunner, placed
     * in creative, leaves none behind (the mock player in the level is always creative).
     */
    private static void spawnItem(GameTestHelper h) {
        HeliEntity heli = GameTestSupport.spawn(h, AH64, new Vec3(8.5, 1, 4.5), 0.0F);
        h.runAfterDelay(2, () -> {
            SeatEntity seat = heli.getSeats()[0];
            ServerPlayer player = h.makeMockServerPlayerInLevel();
            Vec3 aim = seat.getBoundingBox().getCenter();
            Vec3 eye = aim.add(-2.5, 0.0, 0.0);
            player.snapTo(eye.x, eye.y - player.getEyeHeight(), eye.z, -90.0F, 0.0F);
            Scoreboard scoreboard = h.getLevel().getScoreboard();
            PlayerTeam team = scoreboard.addPlayerTeam("gunner_item");
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
            try {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SPAWN_GUNNER_VS_MONSTER.get()));
                check(h, SpawnGunnerItem.findTarget(player) == seat, "the player aims at the seat, not "
                        + SpawnGunnerItem.findTarget(player));
                ModItems.SPAWN_GUNNER_VS_MONSTER.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
                check(h, seat.getRider() instanceof GunnerEntity, "a gunner took the seat");
                GunnerEntity gunner = (GunnerEntity) seat.getRider();
                check(h, player.getMainHandItem().getCount() == 1, "a creative player keeps the item");
                check(h, gunner.isCreative && gunner.targetType == GunnerEntity.VS_MONSTER, "a creative gunner vs monster");
                check(h, gunner.getTeamName().equals(team.getName()), "the gunner joined the player's team");

                check(h, SpawnGunnerItem.findTarget(player) == gunner, "the player now aims at the gunner");
                ModItems.SPAWN_GUNNER_VS_MONSTER.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
                check(h, gunner.isRemoved() && seat.getRider() == null, "the gunner was taken off");
                List<ItemEntity> items = h.getLevel().getEntitiesOfClass(ItemEntity.class,
                        new AABB(gunner.blockPosition()).inflate(3.0));
                check(h, items.isEmpty(), "a creative gunner left an item");
            } finally {
                scoreboard.removePlayerTeam(team);
                player.discard();
            }
            GameTestSupport.cleanUp(heli);
            h.succeed();
        });
    }

    /** Off a vehicle, a gunner goes away after a second and leaves its item, unless placed in creative. */
    private static void offVehicle(GameTestHelper h) {
        Vec3 pos = h.absoluteVec(new Vec3(4.5, 1, 4.5));
        GunnerEntity gunner = gunner(h, GunnerEntity.VS_PLAYER);
        gunner.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        h.getLevel().addFreshEntity(gunner);
        GunnerEntity creative = gunner(h, GunnerEntity.VS_MONSTER);
        creative.isCreative = true;
        creative.snapTo(pos.x + 4, pos.y, pos.z, 0.0F, 0.0F);
        h.getLevel().addFreshEntity(creative);
        h.runAfterDelay(30, () -> {
            check(h, gunner.isRemoved() && creative.isRemoved(), "the gunners went away");
            List<ItemEntity> items = h.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(Vec3.ZERO, Vec3.ZERO).move(pos).inflate(8.0));
            check(h, items.size() == 1 && items.getFirst().getItem().is(ModItems.SPAWN_GUNNER_VS_PLAYER.get()),
                    "only the survival gunner left its item: " + items.size());
            items.forEach(ItemEntity::discard);
            h.succeed();
        });
        check(h, Mth.equal(gunner.getHealth(), gunner.getMaxHealth()), "a fresh gunner");
    }
}
