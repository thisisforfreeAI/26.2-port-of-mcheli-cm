package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;
import static io.github.mcheliceport.gametest.GameTestSupport.cleanUp;
import static io.github.mcheliceport.gametest.GameTestSupport.spawn;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.entity.chain.ChainEntity;
import io.github.mcheliceport.entity.container.CargoContainerEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.registry.ModCriteriaTriggers;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Upstream's advancement triggers: each of the six advancements loads and is earned by its
 * trigger, boarding a UH-1C earns Ride Of The Valkyries, and unhitching a towed container high
 * above the ground earns Relief supplies, but not near the ground.
 */
final class AdvancementGameTests {

    private static final List<String> ADVANCEMENTS = List.of("root", "supply_fuel", "supply_ammo",
            "relief_supplies", "ride_valkyries", "aint_war_hell");

    private AdvancementGameTests() {
    }

    static void register() {
        ModGameTests.test("advancements_load_and_trigger", 20, AdvancementGameTests::loadAndTrigger);
        ModGameTests.test("advancement_ride_valkyries", 40, AdvancementGameTests::valkyries);
        ModGameTests.test("advancement_relief_supplies", 40, AdvancementGameTests::relief);
    }

    private static AdvancementHolder advancement(GameTestHelper h, String name) {
        AdvancementHolder holder = h.getLevel().getServer().getAdvancements()
                .get(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "main/" + name));
        check(h, holder != null, "advancement " + name + " loaded");
        return holder;
    }

    private static boolean done(ServerPlayer player, AdvancementHolder holder) {
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static void loadAndTrigger(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        var triggers = List.of(ModCriteriaTriggers.PUT_AIRCRAFT, ModCriteriaTriggers.SUPPLY_FUEL,
                ModCriteriaTriggers.SUPPLY_AMMO, ModCriteriaTriggers.RELIEF_SUPPLIES,
                ModCriteriaTriggers.RIDING_VALKYRIES, ModCriteriaTriggers.VILLAGER_HURT_BULLET);
        for (int i = 0; i < ADVANCEMENTS.size(); i++) {
            AdvancementHolder holder = advancement(h, ADVANCEMENTS.get(i));
            check(h, !done(player, holder), ADVANCEMENTS.get(i) + " starts unearned");
            ModCriteriaTriggers.trigger(triggers.get(i), player);
            check(h, done(player, holder), ADVANCEMENTS.get(i) + " earned by its trigger");
        }
        h.succeed();
    }

    private static void valkyries(GameTestHelper h) {
        HeliEntity heli = spawn(h, "uh-1c", new Vec3(8.5, 1.0, 8.5), 0.0F);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(heli.position().add(2, 0, 0));
        AdvancementHolder holder = advancement(h, "ride_valkyries");
        h.runAfterDelay(5, () -> {
            heli.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            if (heli.getPilot() != player) {
                // A closed canopy opens on the first use.
                h.runAfterDelay(40, () -> heli.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO));
            }
        });
        h.succeedWhen(() -> {
            check(h, heli.getPilot() == player, "the player boards the UH-1C");
            check(h, done(player, holder), "boarding the UH-1C earns Ride Of The Valkyries");
            player.stopRiding();
            cleanUp(heli);
        });
    }

    private static void relief(GameTestHelper h) {
        HeliEntity high = spawn(h, "uh-1c", new Vec3(4.5, 26.0, 4.5), 0.0F);
        HeliEntity low = spawn(h, "uh-1c", new Vec3(12.5, 1.0, 12.5), 0.0F);
        ServerPlayer highPilot = h.makeMockServerPlayerInLevel();
        ServerPlayer lowPilot = h.makeMockServerPlayerInLevel();
        ChainEntity highChain = tow(h, high, new Vec3(4.5, 20.0, 4.5));
        ChainEntity lowChain = tow(h, low, new Vec3(12.5, 1.0, 8.5));
        AdvancementHolder holder = advancement(h, "relief_supplies");
        ModNetwork.unhitch(lowPilot, low);
        check(h, lowChain.isRemoved(), "the low helicopter lets go of its container");
        check(h, !done(lowPilot, holder), "a drop near the ground earns nothing");
        ModNetwork.unhitch(highPilot, high);
        check(h, highChain.isRemoved(), "the high helicopter lets go of its container");
        check(h, done(highPilot, holder), "a drop from more than 20 blocks earns Relief supplies");
        cleanUp(high);
        cleanUp(low);
        h.succeed();
    }

    private static ChainEntity tow(GameTestHelper h, HeliEntity heli, Vec3 relative) {
        var crate = new CargoContainerEntity(ModEntityTypes.CONTAINER.get(), h.getLevel());
        crate.setPos(h.absoluteVec(relative));
        h.getLevel().addFreshEntity(crate);
        var chain = new ChainEntity(ModEntityTypes.CHAIN.get(), h.getLevel());
        chain.setPos(crate.position());
        chain.setLength(6);
        h.getLevel().addFreshEntity(chain);
        check(h, chain.connect(crate, heli), "the container is chained to the helicopter");
        return chain;
    }
}
