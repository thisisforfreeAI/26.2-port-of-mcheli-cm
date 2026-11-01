package io.github.mcheliceport.platform.fabric.gametest;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.platform.Platform;
import io.github.mcheliceport.platform.loader.PartEntityBase;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.wingman.handler.ProjectileChunkLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Stage 3 of docs/FABRIC_PLAN.md on a Fabric server: the content loaded, every stock vehicle
 * spawns and ticks, and the event hub's pieces (commands, generated packs, chunk tickets, hit-box
 * lookups, persistent data) work. Run with {@code ./gradlew -PwithFabric :fabric:runGameTestServer}.
 * The full suite is the NeoForge build's ({@code ./gradlew runGameTestServer}).
 */
public final class FabricServerGameTests {

    @GameTest(maxTicks = 100)
    public void everyStockVehicleSpawns(GameTestHelper helper) {
        List<AircraftEntity> spawned = new ArrayList<>();
        int i = 0;
        for (ModItems.ContentItem content : ModItems.contentItems()) {
            if (!(content.item().get() instanceof AircraftItem item)) {
                continue;
            }
            Vec3 at = helper.absoluteVec(new Vec3(4, 60 + 12 * (i++ % 10), 4));
            AircraftEntity aircraft = item.createAircraft(helper.getLevel(), at.x, at.y, at.z);
            helper.assertTrue(aircraft != null, "no aircraft from " + content.contentName());
            helper.assertTrue(helper.getLevel().addFreshEntity(aircraft), content.contentName() + " was not added");
            spawned.add(aircraft);
        }
        helper.assertTrue(spawned.size() > 100, "only " + spawned.size() + " stock vehicles");
        helper.runAfterDelay(20, () -> {
            for (AircraftEntity aircraft : spawned) {
                helper.assertTrue(!aircraft.isRemoved(), aircraft.getAircraftTypeName() + " is gone after 20 ticks");
                aircraft.discard();
            }
            helper.succeed();
        });
    }

    @GameTest
    public void hitBoxesAreFoundByEntityLookups(GameTestHelper helper) {
        AircraftEntity heli = ModItems.contentItems().stream()
                .filter(c -> c.type() == ContentType.HELICOPTER && c.item().get() instanceof AircraftItem)
                .map(c -> ((AircraftItem) c.item().get()).createAircraft(helper.getLevel(), 0, 0, 0))
                .filter(a -> a != null && a.partEntities().length > 0)
                .findFirst().orElseThrow();
        Vec3 at = helper.absoluteVec(new Vec3(4, 40, 4));
        heli.setPos(at);
        helper.getLevel().addFreshEntity(heli);
        // The hit boxes move to the vehicle when it ticks.
        helper.runAfterDelay(2, () -> {
            var parts = helper.getLevel().getEntities((net.minecraft.world.entity.Entity) null,
                    heli.getBoundingBox().inflate(16), e -> e instanceof PartEntityBase<?>);
            helper.assertTrue(!parts.isEmpty(), "getEntities did not see the hit boxes");
            var part = heli.partEntities()[0];
            helper.assertTrue(helper.getLevel().getEntityOrPart(part.getId()) == part,
                    "getEntityOrPart did not find a hit box");
            heli.discard();
            helper.succeed();
        });
    }

    @GameTest
    public void serverHooksWork(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.getCommands().getDispatcher().getRoot().getChild("mcheli") != null, "/mcheli missing");
        helper.assertTrue(server.getCommands().getDispatcher().getRoot().getChild("wingman") != null, "/wingman missing");
        helper.assertTrue(server.getPackRepository().getSelectedIds().contains(McheliCePort.MOD_ID + "/content_recipes"),
                "the generated recipe pack is not enabled");

        ChunkPos chunk = new ChunkPos(helper.absolutePos(net.minecraft.core.BlockPos.ZERO).getX() >> 4,
                helper.absolutePos(net.minecraft.core.BlockPos.ZERO).getZ() >> 4);
        UUID owner = UUID.randomUUID();
        helper.assertTrue(ProjectileChunkLoader.TICKETS.forceChunk(helper.getLevel(), owner, chunk.x(), chunk.z(), true, false),
                "adding a ticket changed nothing");
        helper.assertTrue(!ProjectileChunkLoader.TICKETS.forceChunk(helper.getLevel(), owner, chunk.x(), chunk.z(), true, false),
                "a second add by the same owner changed something");
        helper.assertTrue(ProjectileChunkLoader.TICKETS.forceChunk(helper.getLevel(), owner, chunk.x(), chunk.z(), false, false),
                "removing the ticket changed nothing");

        var marker = net.minecraft.world.entity.EntityTypes.MARKER.create(helper.getLevel(),
                net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        Platform.get().persistentData(marker).putBoolean("probe", true);
        helper.assertTrue(Platform.get().persistentData(marker).getBooleanOr("probe", false), "persistent data lost");
        helper.succeed();
    }
}
