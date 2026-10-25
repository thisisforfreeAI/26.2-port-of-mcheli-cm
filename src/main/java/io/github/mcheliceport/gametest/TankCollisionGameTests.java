package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;
import static io.github.mcheliceport.gametest.GameTestSupport.withConfig;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.BoundingBoxInfo;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.content.aircraft.TankInfo;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.entity.tank.GroundCollisionBlocks;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.net.AircraftMotionPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real server ticks run collision code; stationary fixtures isolate it from the tested drive physics. */
final class TankCollisionGameTests {
    private TankCollisionGameTests() { }

    static void register() {
        ModGameTests.test("tank_crushing_defaults", 30, TankCollisionGameTests::defaults);
        ModGameTests.test("tank_crushing_configuration", 30, TankCollisionGameTests::configuration);
        ModGameTests.test("tank_crushing_columns_negative_y", 30, TankCollisionGameTests::columns);
        ModGameTests.test("tank_ram_both_authorities", 30, TankCollisionGameTests::authority);
        ModGameTests.test("tank_ram_occupants_and_exclusions", 30, TankCollisionGameTests::exclusions);
        ModGameTests.test("tank_ram_vehicle_rules", 30, TankCollisionGameTests::vehicles);
        ModGameTests.test("tank_extra_box_crushing_ram_era", 30, TankCollisionGameTests::extraBoxes);
        ModGameTests.test("tank_extra_box_solid", 30, TankCollisionGameTests::solidBoxes);
    }

    private static TankInfo info(int weight) {
        TankInfo info = new TankInfo(ContentLocation.builtin("collision_fixture"), "collision_fixture.yml", "yaml");
        info.weightType = weight; info.maxHp = 1000; info.speed = 1;
        info.armorMinDamage = 0; info.armorMaxDamage = 1000; info.armorDamageFactor = 1;
        info.seatList.add(new SeatInfo(Vec3.ZERO, false));
        info.seatList.add(new SeatInfo(Vec3.ZERO, false));
        return info;
    }

    private static TankEntity tank(GameTestHelper h, TankInfo definition) {
        var tank = new TankEntity(ModEntityTypes.TANK.get(), h.getLevel()) {
            @Override protected AircraftInfo lookupInfo(String name) { return definition; }
            @Override protected void onUpdateAircraft() { }
        };
        tank.setPos(h.absoluteVec(new Vec3(12.5, 6, 12.5)));
        tank.setAircraftTypeName("collision_fixture");
        h.getLevel().addFreshEntity(tank);
        return tank;
    }

    private static net.minecraft.world.entity.animal.cow.Cow cow(GameTestHelper h, Vec3 pos) {
        var cow = EntityTypes.COW.create(h.getLevel(), EntitySpawnReason.COMMAND);
        cow.setNoAi(true); cow.setNoGravity(true); cow.setPos(pos);
        h.getLevel().addFreshEntity(cow);
        return cow;
    }

    private static void ticks(TankEntity tank, int count) { for (int i = 0; i < count; i++) { tank.tick(); } }

    private static void defaults(GameTestHelper h) {
        withConfig(CommonConfig.CAR_BREAKABLE_BLOCKS, GroundCollisionBlocks.CAR, () ->
                withConfig(CommonConfig.TANK_BREAKABLE_BLOCKS, GroundCollisionBlocks.TANK, () -> defaultBlocks(h)));
    }

    private static void defaultBlocks(GameTestHelper h) {
        var car = tank(h, info(1));
        h.setBlock(12, 5, 12, Blocks.GLASS_PANE);
        h.setBlock(13, 5, 12, Blocks.GLASS);
        h.setBlock(14, 5, 12, Blocks.OAK_PLANKS);
        h.setBlock(13, 5, 13, Blocks.STONE);
        h.setBlock(10, 4, 10, Blocks.END_STONE); h.setBlock(10, 5, 10, Blocks.CHORUS_FLOWER);
        h.setBlock(12, 5, 13, Blocks.BROWN_MUSHROOM_BLOCK);
        h.setBlock(14, 4, 14, Blocks.STONE); h.setBlock(14, 5, 14, Blocks.FLOWER_POT);
        h.setBlock(14, 4, 11, Blocks.STONE); h.setBlock(14, 5, 11, Blocks.BANNER.pick(net.minecraft.world.item.DyeColor.WHITE));
        h.setBlock(12, 4, 14, Blocks.STONE); h.setBlock(12, 5, 14, Blocks.SKELETON_SKULL);
        car.tick();
        check(h, h.getBlockState(new BlockPos(12, 5, 12)).isAir(), "car crushes glass panes");
        check(h, h.getBlockState(new BlockPos(13, 5, 12)).is(Blocks.GLASS) && h.getBlockState(new BlockPos(14, 5, 12)).is(Blocks.OAK_PLANKS),
                "car leaves full glass and wood intact");
        check(h, h.getBlockState(new BlockPos(10, 5, 10)).isAir(), "legacy PLANTS includes chorus flowers");
        check(h, h.getBlockState(new BlockPos(12, 5, 13)).is(Blocks.BROWN_MUSHROOM_BLOCK)
                && h.getBlockState(new BlockPos(14, 5, 14)).is(Blocks.FLOWER_POT)
                && h.getBlockState(new BlockPos(14, 5, 11)).is(Blocks.BANNER.pick(net.minecraft.world.item.DyeColor.WHITE)), "car leaves wood/circuit categories intact");
        car.discard();
        var tank = tank(h, info(2));
        h.setBlock(10, 5, 12, Blocks.GLOWSTONE);
        h.setBlock(10, 6, 12, Blocks.OAK_PLANKS);
        h.setBlock(11, 4, 12, Blocks.STONE);
        h.setBlock(11, 5, 12, Blocks.TORCH);
        h.setBlock(11, 6, 12, Blocks.OAK_PLANKS);
        tank.tick();
        check(h, h.getBlockState(new BlockPos(13, 5, 12)).isAir() && h.getBlockState(new BlockPos(14, 5, 12)).isAir(), "heavy tank crushes full glass and wood");
        check(h, h.getBlockState(new BlockPos(10, 5, 12)).is(Blocks.GLOWSTONE) && h.getBlockState(new BlockPos(10, 6, 12)).is(Blocks.OAK_PLANKS),
                "protected glowstone stops its column");
        check(h, h.getBlockState(new BlockPos(11, 5, 12)).is(Blocks.TORCH) && h.getBlockState(new BlockPos(11, 6, 12)).is(Blocks.OAK_PLANKS),
                "protected torch stops its column");
        check(h, h.getBlockState(new BlockPos(13, 5, 13)).is(Blocks.STONE), "ordinary terrain stays intact");
        check(h, h.getBlockState(new BlockPos(12, 5, 13)).isAir()
                && h.getBlockState(new BlockPos(14, 5, 14)).isAir()
                && h.getBlockState(new BlockPos(14, 5, 11)).isAir()
                && h.getBlockState(new BlockPos(12, 5, 14)).isAir(), "legacy WOOD/CIRCUITS includes mushrooms, banners, flower pots and skulls");
        tank.discard(); h.succeed();
    }

    private static void configuration(GameTestHelper h) {
        var tank = tank(h, info(2));
        h.setBlock(12, 5, 12, Blocks.OAK_PLANKS);
        withConfig(CommonConfig.COLLISION_DESTROY_BLOCK, false, tank::tick);
        check(h, h.getBlockState(new BlockPos(12, 5, 12)).is(Blocks.OAK_PLANKS), "global crushing switch respected");
        withConfig(CommonConfig.TANK_BREAKABLE_BLOCKS, List.of(), tank::tick);
        check(h, h.getBlockState(new BlockPos(12, 5, 12)).is(Blocks.OAK_PLANKS), "empty rules disable material crushing");
        tank.discard();
        var car = tank(h, info(1));
        h.setBlock(12, 5, 12, Blocks.STONE);
        withConfig(CommonConfig.CAR_BREAKABLE_BLOCKS, List.of("minecraft:stone"), car::tick);
        check(h, h.getBlockState(new BlockPos(12, 5, 12)).isAir(), "custom block selectors are applied immediately");
        car.discard();
        var light = tank(h, info(0)); h.setBlock(12, 5, 12, Blocks.GLASS_PANE); light.tick();
        check(h, h.getBlockState(new BlockPos(12, 5, 12)).is(Blocks.GLASS_PANE), "unspecified weight has no crushing list");
        light.discard(); h.succeed();
    }

    private static void columns(GameTestHelper h) {
        var tank = tank(h, info(2));
        // Exercise actual negative world heights, independent of the test structure's altitude.
        tank.setPos(tank.getX(), -20, tank.getZ());
        BlockPos lower = BlockPos.containing(tank.getX() - 0.5, -22, tank.getZ() - 0.5);
        BlockPos upper = lower.above();
        h.getLevel().setBlockAndUpdate(lower, Blocks.OAK_PLANKS.defaultBlockState());
        h.getLevel().setBlockAndUpdate(upper, Blocks.GLASS.defaultBlockState());
        tank.tick();
        check(h, h.getLevel().getBlockState(lower).isAir() && h.getLevel().getBlockState(upper).is(Blocks.GLASS),
                "only the first breakable block in a column is crushed per pass, below Y zero");
        tank.tick(); check(h, h.getLevel().getBlockState(upper).isAir(), "next tick reaches the next block");
        tank.discard(); h.succeed();
    }

    private static void authority(GameTestHelper h) {
        for (boolean serverAuthority : new boolean[] {false, true}) {
            withConfig(ServerConfig.SERVER_AUTHORITATIVE_MOVEMENT, serverAuthority, () -> {
                var tank = tank(h, info(2)); tank.tick();
                var pilot = h.makeMockServerPlayerInLevel(); pilot.setGameMode(GameType.SURVIVAL);
                check(h, pilot.startRiding(tank, true, true), "network pilot boards");
                check(h, tank.followsPilot() != serverAuthority, "authority setting applied");
                var cow = cow(h, tank.position());
                // Isolate the explicit ramming impulse from vanilla's attacker-relative damage knockback.
                cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
                if (serverAuthority) { tank.setDeltaMovement(0.2, 0, 0); }
                else { ModNetwork.applyPilotMotion(tank, new AircraftMotionPayload(tank.getId(), 0.2, 0, 0, 0)); }
                tank.tick();
                check(h, cow.getHealth() < cow.getMaxHealth() && cow.getDeltaMovement().x >= 0.3 - 1.0E-6,
                        "server applies ramming and knockback, authority=" + serverAuthority + " health=" + cow.getHealth()
                                + " motion=" + cow.getDeltaMovement() + " tankMotion=" + tank.getDeltaMovement());
                check(h, cow.getLastDamageSource() != null && cow.getLastDamageSource().getEntity() == pilot
                        && cow.getLastDamageSource().is(DamageTypes.MOB_ATTACK), "pilot gets damage credit without triggering vehicle pickup");
                cow.discard(); tank.discard(); pilot.discard();
            });
        }
        h.succeed();
    }

    private static void exclusions(GameTestHelper h) {
        var tank = tank(h, info(2)); tank.tick();
        var pilot = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(12, 7, 12)); pilot.startRiding(tank, true, true);
        var crew = cow(h, tank.position()); crew.startRiding(tank.getSeats()[0], true, true);
        ticks(tank, 9); // Upstream remembers occupants at tick 10, and decrements the grace counter every 10 ticks.
        tank.setDeltaMovement(0.2, 0, 0); tank.tick();
        check(h, crew.getHealth() == crew.getMaxHealth(), "crew excluded while aboard");
        crew.stopRiding(); crew.setPos(tank.position());
        var item = new ItemEntity(h.getLevel(), tank.getX(), tank.getY(), tank.getZ(), new ItemStack(Items.DIAMOND));
        var xp = new ExperienceOrb(h.getLevel(), tank.getX(), tank.getY(), tank.getZ(), 1);
        item.setDeltaMovement(Vec3.ZERO); xp.setDeltaMovement(Vec3.ZERO);
        var bullet = new BulletEntity(ModEntityTypes.BULLET.get(), h.getLevel()); bullet.setPos(tank.position());
        var arrow = EntityTypes.ARROW.create(h.getLevel(), EntitySpawnReason.COMMAND); arrow.setPos(tank.position());
        h.getLevel().addFreshEntity(item); h.getLevel().addFreshEntity(xp); h.getLevel().addFreshEntity(bullet); h.getLevel().addFreshEntity(arrow);
        ticks(tank, 59);
        check(h, crew.getHealth() == crew.getMaxHealth(), "recently dismounted crew retain upstream grace period");
        check(h, !item.isRemoved() && !xp.isRemoved() && !bullet.isRemoved() && arrow.isRemoved(), "items/XP/projectiles excluded; arrows cleared");
        check(h, item.getDeltaMovement().horizontalDistanceSqr() == 0 && xp.getDeltaMovement().horizontalDistanceSqr() == 0,
                "ignored entities receive no ramming impulse");
        ticks(tank, 10);
        check(h, crew.getHealth() < crew.getMaxHealth(), "grace period eventually expires");
        crew.discard(); item.discard(); xp.discard(); bullet.discard(); tank.discard(); h.succeed();
    }

    private static void vehicles(GameTestHelper h) {
        var attacker = tank(h, info(2)); var heavy = tank(h, info(2));
        attacker.setDeltaMovement(0.04, 0, 0); attacker.tick();
        check(h, heavy.getDamageTaken() == 0, "slow contact causes no ramming damage");
        attacker.setDeltaMovement(0.2, 0, 0); attacker.tick();
        check(h, heavy.getDamageTaken() == 0, "heavy tanks protected by default");
        withConfig(CommonConfig.COLLISION_ENTITY_DAMAGE, false, () ->
                withConfig(CommonConfig.COLLISION_ENTITY_TANK_DAMAGE, true, attacker::tick));
        check(h, heavy.getDamageTaken() == 8, "heavy tank damage has its own switch and upstream speed/proximity formula");
        attacker.discard(); heavy.discard();
        var car = tank(h, info(1)); TankInfo targetInfo = info(0);
        // Three overlapping modern parts must not cause three extra hits in the same region query.
        for (int i = 0; i < 3; i++) { targetInfo.extraBoundingBox.add(new BoundingBoxInfo(0, 0, 0, 2, 2, 1)); }
        var target = tank(h, targetInfo);
        car.setDeltaMovement(0.2, 0, 0);
        withConfig(CommonConfig.COLLISION_ENTITY_DAMAGE, false, car::tick);
        check(h, target.getDamageTaken() == 0, "general entity damage switch respected");
        car.tick();
        check(h, target.getDamageTaken() == 8 && car.getDamageTaken() == 2,
                "multipart target hit once and car takes one-third reciprocal damage: " + target.getDamageTaken() + "/" + car.getDamageTaken());
        check(h, Math.abs(target.getDeltaMovement().x - 0.01) < 1.0E-6, "aircraft use reduced knockback");
        car.discard(); target.discard(); h.succeed();
    }

    private static void extraBoxes(GameTestHelper h) {
        TankInfo info = info(2);
        var box = new BoundingBoxInfo(6, 0, 0, 2, 2, 1); box.isERA = true; box.eraMinDamage = 5;
        info.extraBoundingBox.add(box);
        var tank = tank(h, info); tank.getRandom().setSeed(0);
        var victim = cow(h, tank.position().add(6, 0, 0));
        h.setBlock(18, 5, 12, Blocks.OAK_PLANKS);
        tank.setDeltaMovement(0.2, 0, 0); ticks(tank, 30);
        check(h, h.getBlockState(new BlockPos(18, 5, 12)).isAir() && victim.getHealth() < victim.getMaxHealth(),
                "sampled extra box crushes and rams beyond the main hull");
        victim.discard();
        tank.hurtFromHitBox(h.getLevel(), tank.getHitBoxes()[0], h.getLevel().damageSources().generic(), 20);
        check(h, !tank.isEraActive(0), "fixture ERA tile popped");
        var next = cow(h, tank.position().add(6, 0, 0)); h.setBlock(18, 5, 12, Blocks.OAK_PLANKS);
        ticks(tank, 30);
        check(h, h.getBlockState(new BlockPos(18, 5, 12)).is(Blocks.OAK_PLANKS) && next.getHealth() == next.getMaxHealth(),
                "popped tile no longer crushes or rams");
        next.discard(); tank.discard(); h.succeed();
    }

    private static void solidBoxes(GameTestHelper h) {
        TankInfo info = info(2);
        info.extraBoundingBox.add(new BoundingBoxInfo(6, 0, 0, 2, 2, 1));
        var era = new BoundingBoxInfo(-6, 0, 0, 2, 2, 1); era.isERA = true; era.eraMinDamage = 5;
        info.extraBoundingBox.add(era);
        var tank = tank(h, info); ticks(tank, 1);
        var hull = tank.getHitBoxes()[0];
        var cow = cow(h, tank.position().add(8.5, 0, 0));
        cow.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(-3, 0, 0));
        double front = hull.getBoundingBox().maxX;
        check(h, Math.abs(cow.getBoundingBox().minX - front) < 1.0E-3,
                "a mob walking into the extra hull box stops at it: " + cow.getBoundingBox().minX + " vs " + front);
        cow.setPos(hull.getBoundingBox().getCenter().x, cow.getY(), cow.getZ());
        double inside = cow.getX();
        cow.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(3, 0, 0));
        check(h, cow.getX() > inside + 2.9, "a mob already inside the box can move out of it");
        var rider = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(12, 7, 12)); rider.startRiding(tank, true, true);
        check(h, hull.canBeCollidedWith(cow) && !hull.canBeCollidedWith(tank) && !hull.canBeCollidedWith(rider)
                && !hull.canBeCollidedWith(tank.getHitBoxes()[1]), "the box is solid for others, not for the tank and its crew");
        var popped = tank.getHitBoxes()[1];
        check(h, popped.canBeCollidedWith(cow), "an intact ERA tile is solid");
        tank.hurtFromHitBox(h.getLevel(), popped, h.getLevel().damageSources().generic(), 20);
        check(h, !popped.canBeCollidedWith(cow), "a popped ERA tile is not");
        var plane = new io.github.mcheliceport.entity.plane.PlaneEntity(ModEntityTypes.PLANE.get(), h.getLevel());
        check(h, !plane.hasSolidHitBoxes(), "aircraft keep their extra boxes for hits only");
        rider.stopRiding(); rider.discard(); cow.discard(); tank.discard(); h.succeed();
    }
}
