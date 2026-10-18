package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.item.RangeFinderItem;
import io.github.mcheliceport.item.WrenchItem;
import io.github.mcheliceport.multiplay.Spotting;
import io.github.mcheliceport.multiplay.TargetType;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * The wrench, the rangefinder and team spotting (docs/M6_PLAN.md), against upstream's
 * {@code MCH_ItemWrench}, {@code MCH_ItemRangeFinder}, {@code PacketRequestSpotEnemy} and
 * {@code MCH_Multiplay}.
 */
final class ToolGameTests {

    private ToolGameTests() {
    }

    static void register() {
        ModGameTests.test("tools_wrench_repairs_aimed_vehicle", 40, ToolGameTests::wrenchRepair);
        ModGameTests.test("tools_wrench_mining_and_hits", 20, ToolGameTests::wrenchTool);
        ModGameTests.test("tools_rangefinder_spots_and_charges", 40, ToolGameTests::rangeFinderSpot);
        ModGameTests.test("tools_rangefinder_mark_point", 40, ToolGameTests::markPoint);
        ModGameTests.test("tools_rangefinder_can_use", 20, ToolGameTests::canUse);
        ModGameTests.test("tools_spotting_filters_and_teams", 40, ToolGameTests::filtersAndTeams);
    }

    private static HeliEntity heli(GameTestHelper h, Vec3 relative) {
        var definition = new HeliInfo(ContentLocation.builtin("tool_fixture"), "tool_fixture.yml", "yaml");
        definition.maxHp = 200;
        var ac = new HeliEntity(ModEntityTypes.HELICOPTER.get(), h.getLevel()) {
            @Override protected AircraftInfo lookupInfo(String name) { return definition; }
            @Override protected void onUpdateAircraft() { }
        };
        ac.setAircraftTypeName("tool_fixture");
        ac.setPos(h.absoluteVec(relative));
        h.getLevel().addFreshEntity(ac);
        ac.tick();
        return ac;
    }

    private static ServerPlayer player(GameTestHelper h, GameType mode, Vec3 relative) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(mode);
        Vec3 pos = h.absoluteVec(relative);
        p.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        return p;
    }

    /**
     * A mock player with its own name: {@code makeMockServerPlayerInLevel} names every mock player
     * alike, so they would all share one scoreboard team.
     */
    private static ServerPlayer named(GameTestHelper h, String name, Vec3 relative) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name), false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(),
                cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        p.setGameMode(GameType.SURVIVAL);
        Vec3 pos = h.absoluteVec(relative);
        p.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        return p;
    }

    private static ItemStack hold(ServerPlayer p, ItemStack stack) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return p.getMainHandItem();
    }

    private static void wrenchRepair(GameTestHelper h) {
        HeliEntity ac = heli(h, new Vec3(8, 2, 8));
        ServerPlayer p = player(h, GameType.SURVIVAL, new Vec3(8, 2, 5));
        p.lookAt(EntityAnchorArgument.Anchor.EYES, ac.getBoundingBox().getCenter());
        ItemStack wrench = hold(p, new ItemStack(ModItems.WRENCH.get()));
        WrenchItem item = (WrenchItem) wrench.getItem();
        check(h, WrenchItem.aimedAircraft(p) == ac, "the wrench sees the vehicle in front");
        ac.setDamageTaken(100);
        item.onUseTick(h.getLevel(), p, wrench, 72000 - 20);
        check(h, ac.getDamageTaken() == 90 && wrench.getDamageValue() == 1, "a second of holding repairs 10 for 1 durability: "
                + ac.getDamageTaken() + ", " + wrench.getDamageValue());
        item.onUseTick(h.getLevel(), p, wrench, 72000 - 21);
        item.onUseTick(h.getLevel(), p, wrench, 72000);
        check(h, ac.getDamageTaken() == 90, "only every 20th tick of use repairs, not the first");
        ac.setDamageTaken(0);
        item.onUseTick(h.getLevel(), p, wrench, 72000 - 40);
        check(h, wrench.getDamageValue() == 1, "an undamaged vehicle costs no durability");
        ac.setDamageTaken(50);
        p.setYRot(180.0F);
        p.setYHeadRot(180.0F);
        item.onUseTick(h.getLevel(), p, wrench, 72000 - 60);
        check(h, ac.getDamageTaken() == 50, "looking away repairs nothing");
        p.lookAt(EntityAnchorArgument.Anchor.EYES, ac.getBoundingBox().getCenter());
        BlockPos wall = BlockPos.containing(p.getEyePosition().add(ac.getBoundingBox().getCenter()).scale(0.5));
        h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        check(h, WrenchItem.aimedAircraft(p) == null, "a block in between hides the vehicle");
        h.getLevel().removeBlock(wall, false);
        check(h, WrenchItem.aimedAircraft(p) == ac, "seen again once the block is gone");
        p.setPos(p.getX(), p.getY(), p.getZ() - 6.0);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, ac.getBoundingBox().getCenter());
        check(h, WrenchItem.aimedAircraft(p) == null, "out of the wrench's four-block reach");
        p.discard();
        ac.discard();
        h.succeed();
    }

    private static void wrenchTool(GameTestHelper h) {
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        check(h, wrench.getMaxDamage() == 250, "iron tool durability");
        check(h, wrench.getDestroySpeed(Blocks.IRON_BLOCK.defaultBlockState()) == 20.5F, "iron blocks mine fast");
        check(h, wrench.getDestroySpeed(Blocks.REDSTONE_WIRE.defaultBlockState()) == 5.5F, "circuits at 5.5");
        check(h, wrench.getDestroySpeed(Blocks.STONE.defaultBlockState()) == 2.0F, "everything else at 2");
        check(h, wrench.isCorrectToolForDrops(Blocks.IRON_BLOCK.defaultBlockState()), "harvests iron blocks");
        check(h, !wrench.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()), "not stone");
        ServerPlayer p = player(h, GameType.SURVIVAL, new Vec3(2, 2, 2));
        ItemStack held = hold(p, wrench);
        Mob cow = h.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(3, 2, 2));
        held.postHurtEnemy(cow, p);
        check(h, held.getDamageValue() == 2, "a hit costs 2 durability: " + held.getDamageValue());
        held.mineBlock(h.getLevel(), Blocks.STONE.defaultBlockState(), h.absolutePos(new BlockPos(1, 1, 1)), p);
        check(h, held.getDamageValue() == 4, "a block with hardness costs 2 durability");
        check(h, held.getAttributeModifiers().modifiers().stream().anyMatch(m -> m.modifier().amount() == 6.0),
                "attack damage 4 + iron's 2");
        p.discard();
        cow.discard();
        h.succeed();
    }

    private static void rangeFinderSpot(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.SURVIVAL, new Vec3(2, 2, 2));
        RangeFinderItem.resetCooldown(p);
        Mob zombie = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(2, 2, 12));
        p.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.getEyePosition());
        ItemStack finder = hold(p, new ItemStack(ModItems.RANGEFINDER.get()));
        check(h, !RangeFinderItem.handleSpotRequest(p, RangeFinderItem.MODE_VEHICLES_FILTER),
                "players/vehicles mode does not take mobs");
        check(h, finder.getDamageValue() == 1, "a failed spot still uses its charge, as upstream");
        RangeFinderItem.resetCooldown(p);
        check(h, RangeFinderItem.handleSpotRequest(p, RangeFinderItem.MODE_MOBS_FILTER), "mobs mode spots the zombie");
        check(h, finder.getDamageValue() == 2, "one charge per spot");
        check(h, !RangeFinderItem.handleSpotRequest(p, RangeFinderItem.MODE_MOBS_FILTER) && finder.getDamageValue() == 2,
                "the server refuses a second spot inside the cooldown");
        RangeFinderItem.resetCooldown(p);
        finder.setDamageValue(finder.getMaxDamage());
        check(h, !RangeFinderItem.handleSpotRequest(p, RangeFinderItem.MODE_MOBS_FILTER), "an empty rangefinder cannot spot");
        check(h, finder.getDamageValue() == finder.getMaxDamage() && !finder.isEmpty(), "and does not break");
        finder.setDamageValue(finder.getMaxDamage() - 1);
        RangeFinderItem.resetCooldown(p);
        check(h, RangeFinderItem.handleSpotRequest(p, RangeFinderItem.MODE_MOBS_FILTER), "the last charge spots");
        check(h, finder.getDamageValue() == finder.getMaxDamage() && p.getMainHandItem() == finder,
                "leaving an empty rangefinder to recharge");
        finder.setDamageValue(0);
        GameTestSupport.withConfig(ServerConfig.RANGE_FINDER_CONSUME, false, () -> {
            RangeFinderItem.resetCooldown(p);
            RangeFinderItem.handleSpotRequest(p, RangeFinderItem.MODE_MOBS_FILTER);
        });
        check(h, finder.getDamageValue() == 0, "rangeFinderConsume off keeps the charge");
        p.setGameMode(GameType.CREATIVE);
        RangeFinderItem.resetCooldown(p);
        RangeFinderItem.handleSpotRequest(p, RangeFinderItem.MODE_MOBS_FILTER);
        check(h, finder.getDamageValue() == 0, "creative players use no charge");
        p.setGameMode(GameType.SURVIVAL);
        p.setYRot(p.getYRot() + 180.0F);
        RangeFinderItem.resetCooldown(p);
        check(h, !RangeFinderItem.handleSpotRequest(p, RangeFinderItem.MODE_MOBS_FILTER), "nothing behind the player");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        RangeFinderItem.resetCooldown(p);
        check(h, !RangeFinderItem.handleSpotRequest(p, RangeFinderItem.MODE_MOBS_FILTER), "refused without a rangefinder");
        p.discard();
        zombie.discard();
        h.succeed();
    }

    private static void markPoint(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.SURVIVAL, new Vec3(2, 2, 2));
        RangeFinderItem.resetCooldown(p);
        ItemStack finder = hold(p, new ItemStack(ModItems.RANGEFINDER.get()));
        finder.setDamageValue(finder.getMaxDamage());
        p.setXRot(90.0F);
        check(h, RangeFinderItem.handleSpotRequest(p, 0), "marks the floor below, even when empty");
        check(h, finder.getDamageValue() == finder.getMaxDamage(), "marking uses no charge");
        p.discard();
        h.succeed();
    }

    private static void canUse(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.SURVIVAL, new Vec3(8, 2, 8));
        check(h, !RangeFinderItem.canUse(p), "needs the rangefinder in the main hand");
        hold(p, new ItemStack(ModItems.RANGEFINDER.get()));
        check(h, RangeFinderItem.canUse(p), "usable on foot");
        HeliEntity ac = heli(h, new Vec3(8, 2, 8));
        check(h, p.startRiding(ac, true, true), "board as pilot");
        check(h, !RangeFinderItem.canUse(p), "not while flying");
        p.stopRiding();
        p.discard();
        ac.discard();
        h.succeed();
    }

    private static void filtersAndTeams(GameTestHelper h) {
        HeliEntity ac = heli(h, new Vec3(8, 2, 12));
        Mob zombie = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(4, 2, 12));
        Mob cow = h.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(12, 2, 12));
        ServerPlayer spotter = named(h, "mch_spotter", new Vec3(8, 2, 2));
        ServerPlayer mate = named(h, "mch_mate", new Vec3(6, 2, 10));
        ServerPlayer enemy = named(h, "mch_enemy", new Vec3(10, 2, 10));
        check(h, Spotting.matchesFilter(Spotting.FILTER_HELIS, ac) && !Spotting.matchesFilter(Spotting.FILTER_PLANES, ac),
                "helicopters are 16");
        check(h, Spotting.matchesFilter(Spotting.FILTER_MONSTERS, zombie) && !Spotting.matchesFilter(Spotting.FILTER_MOBS, zombie),
                "zombies are monsters");
        check(h, Spotting.matchesFilter(Spotting.FILTER_MOBS, cow) && !Spotting.matchesFilter(Spotting.FILTER_MONSTERS, cow),
                "cows are other mobs");
        check(h, Spotting.matchesFilter(Spotting.FILTER_PLAYERS, enemy), "players are 4");
        Vec3 eyes = spotter.getEyePosition();
        check(h, Spotting.canSpotEntity(spotter, eyes, enemy, false) == TargetType.NONE,
                "a spotter on no team never sees players");
        check(h, Spotting.canSpotEntity(spotter, eyes, cow, false) == TargetType.OTHER_MOB, "mobs show to anyone");
        Scoreboard board = h.getLevel().getScoreboard();
        PlayerTeam blue = board.addPlayerTeam("mch_test_blue");
        PlayerTeam red = board.addPlayerTeam("mch_test_red");
        try {
            board.addPlayerToTeam(spotter.getScoreboardName(), blue);
            board.addPlayerToTeam(mate.getScoreboardName(), blue);
            board.addPlayerToTeam(enemy.getScoreboardName(), red);
            check(h, Spotting.canSpotEntity(spotter, eyes, mate, false) == TargetType.SAME_TEAM_PLAYER, "teammate");
            check(h, Spotting.canSpotEntity(spotter, eyes, enemy, false) == TargetType.OTHER_TEAM_PLAYER, "enemy");
            check(h, Spotting.canSpotEntity(spotter, eyes, ac, false) == TargetType.NONE, "an empty vehicle shows nothing");
            Vec3 enemyHome = enemy.position();
            Vec3 aside = h.absoluteVec(new Vec3(14, 2, 3));
            enemy.snapTo(aside.x, aside.y, aside.z, 0.0F, 0.0F);
            spotter.lookAt(EntityAnchorArgument.Anchor.EYES, mate.getEyePosition());
            check(h, !Spotting.spotEntity(spotter, null, spotter.getEyePosition(), Spotting.FILTER_PLAYERS, 400, 15, 20.0F),
                    "teammates are never spotted");
            spotter.lookAt(EntityAnchorArgument.Anchor.EYES, enemy.getEyePosition());
            check(h, Spotting.spotEntity(spotter, null, spotter.getEyePosition(), Spotting.FILTER_PLAYERS, 400, 15, 20.0F),
                    "an enemy player is");
            enemy.snapTo(enemyHome.x, enemyHome.y, enemyHome.z, 0.0F, 0.0F);
            check(h, enemy.startRiding(ac, true, true), "enemy boards");
            check(h, Spotting.canSpotEntity(spotter, eyes, ac, false) == TargetType.OTHER_TEAM_PLAYER,
                    "a vehicle shows as its crew");
            check(h, !Spotting.isMountedSameTeam(ac, spotter) && Spotting.isMountedSameTeam(ac, enemy), "crew teams");
            spotter.lookAt(EntityAnchorArgument.Anchor.EYES, ac.getBoundingBox().getCenter());
            check(h, Spotting.spotEntity(spotter, null, spotter.getEyePosition(), Spotting.FILTER_HELIS, 400, 15, 20.0F),
                    "the enemy's helicopter is spotted");
            BlockPos wall = h.absolutePos(new BlockPos(8, 3, 6));
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -1; dy <= 6; dy++) {
                    h.getLevel().setBlockAndUpdate(wall.offset(dx, dy, 0), Blocks.STONE.defaultBlockState());
                }
            }
            check(h, Spotting.canSpotEntity(spotter, spotter.getEyePosition(), enemy, true) == TargetType.NONE,
                    "a wall hides the enemy");
            enemy.stopRiding();
        } finally {
            board.removePlayerTeam(blue);
            board.removePlayerTeam(red);
        }
        spotter.discard();
        mate.discard();
        enemy.discard();
        zombie.discard();
        cow.discard();
        ac.discard();
        h.succeed();
    }
}
