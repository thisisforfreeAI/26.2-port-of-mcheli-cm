package io.github.mcheliceport.platform.fabric.gametest;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.registry.ModItems;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;

/**
 * Stage 4 of docs/FABRIC_PLAN.md: the Fabric client boots with the mod, opens a world, draws a
 * stock vehicle and puts the player in it, taking a screenshot at each step (in
 * {@code fabric/build/clientgametest/screenshots}). Run with
 * {@code xvfb-run ./gradlew -PwithFabric :fabric:runClientGameTest}.
 */
public final class FabricClientGameTests implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("mcheli-world");

            AtomicReference<String> spawned = new AtomicReference<>();
            world.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                for (ModItems.ContentItem content : ModItems.contentItems()) {
                    if (content.item().get() instanceof AircraftItem item && content.contentName().equals("ah-64")) {
                        AircraftEntity aircraft = item.createAircraft(player.level(), player.getX(), player.getY(),
                                player.getZ() + 10);
                        if (aircraft != null && player.level().addFreshEntity(aircraft)) {
                            spawned.set(content.contentName());
                        }
                        break;
                    }
                }
            });
            if (spawned.get() == null) {
                throw new AssertionError("no AH-64 spawned");
            }
            context.waitTicks(40);
            context.takeScreenshot("mcheli-vehicle");

            world.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                var aircraft = player.level().getEntitiesOfClass(AircraftEntity.class,
                        player.getBoundingBox().inflate(20)).getFirst();
                player.startRiding(aircraft, true, true);
            });
            context.waitTicks(60);
            boolean riding = context.computeOnClient(mc -> mc.player != null && mc.player.getVehicle() != null);
            context.takeScreenshot("mcheli-cockpit");
            if (!riding) {
                throw new AssertionError("the player is not riding the " + spawned.get());
            }
        }
    }
}
