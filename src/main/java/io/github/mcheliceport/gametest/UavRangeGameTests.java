package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;
import static io.github.mcheliceport.gametest.UavGameTests.*;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.uav.UavChunkView;
import io.github.mcheliceport.entity.uav.UavPositionIndex;
import io.github.mcheliceport.entity.uav.UavRemoteSession;
import io.github.mcheliceport.net.ModNetwork;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

final class UavRangeGameTests {
    private UavRangeGameTests() { }
    static void register() {
        ModGameTests.test("uav_5000_block_boundary_and_config", 60, UavRangeGameTests::boundary);
        ModGameTests.test("uav_distant_streaming_ticks_and_releases", 120, UavRangeGameTests::streaming);
        ModGameTests.test("uav_unloaded_reconnect_probe_cancels_and_rejects_unknown", 60, UavRangeGameTests::probe);
        ModGameTests.test("uav_distant_saved_uuid_reconnects_after_loading", 80, UavRangeGameTests::reconnect);
    }
    private static void boundary(GameTestHelper h) {
        var s = station(h, false); var p = operator(h, s); var ac = deploy(h, s, p, "rc-goblin");
        GameTestSupport.withConfig(ServerConfig.UAV_CONTROL_RANGE, 5000, () -> {
            check(h, s.connect(p), "connect before distant flight");
            ac.setPos(s.position().add(5000, 0, 0)); h.getLevel().getChunk(ac.blockPosition());
            check(h, s.range() == 5000 && ModNetwork.riddenAircraft(p) == ac, "exactly 5000 blocks stays connected");
            ac.setCurrentThrottle(1); ac.switchHoveringMode(true); ac.setPos(s.position().add(5000.01, 0, 0));
            check(h, ModNetwork.riddenAircraft(p) == null, "beyond boundary rejects packets immediately");
            s.tick(); check(h, s.controlled() == null && ac.getCurrentThrottle() == 0 && !ac.isHoveringMode(), "range loss stops controls");
            ac.setPos(s.position().add(4000, 3001, 0)); h.getLevel().getChunk(ac.blockPosition());
            check(h, !s.connect(p), "range includes vertical distance");
        });
        GameTestSupport.withConfig(ServerConfig.UAV_CONTROL_RANGE, 1000, () -> {
            ac.setPos(s.position().add(12, 2, 12)); h.getLevel().getChunk(ac.blockPosition());
            check(h, s.connect(p), "reconnect before testing lower cap");
            ac.setPos(s.position().add(1001, 0, 0)); h.getLevel().getChunk(ac.blockPosition());
            check(h, !s.validLink(ac), "server can lower maximum range");
            ac.setPos(s.position().add(1000, 0, 0)); h.getLevel().getChunk(ac.blockPosition());
            check(h, s.validLink(ac), "lower boundary remains inclusive");
        });
        clean(ac, p, s); h.succeed();
    }
    private static void streaming(GameTestHelper h) {
        var s = station(h, false); var p = h.makeMockServerPlayerInLevel(); p.setGameMode(GameType.SURVIVAL);
        p.setPos(s.position()); p.startRiding(s, true, true); s.positionRider(p);
        var ac = deploy(h, s, p, "rc-goblin"); check(h, s.connect(p), "real server player connects");
        ac.setPos(s.position().add(4800, 20, 0)); ac.setDeltaMovement(Vec3.ZERO); ac.setFuel(ac.getMaxFuel());
        ac.switchHoveringMode(true); ac.setCurrentThrottle(.6); s.tick();
        var origin = p.position(); int ticks = ac.getCountOnUpdate();
        check(h, p.getChunkTrackingView() instanceof UavChunkView, "remote view installed");
        check(h, p.getChunkTrackingView().contains(ac.chunkPosition()) && p.getChunkTrackingView().contains(s.chunkPosition()), "both camera and operator tracked");
        check(h, !p.getChunkTrackingView().contains(s.chunkPosition().x() + 150, s.chunkPosition().z()), "corridor is not streamed");
        check(h, s.remoteTicketCount() > 0 && s.remoteTicketCount() < 500, "bounded camera tickets");
        var cameraChunks = new java.util.HashSet<net.minecraft.world.level.ChunkPos>();
        ((UavChunkView) p.getChunkTrackingView()).camera().forEach(cameraChunks::add);
        var ordinary = p.position().subtract(ac.position());
        check(h, UavRemoteSession.trackingDelta(ordinary, ac, p).equals(Vec3.ZERO), "remote entity tracking origin is aircraft");
        h.runAfterDelay(50, () -> {
            check(h, s.controlled() == ac && ac.getCountOnUpdate() > ticks + 20, "distant UAV ticks without a nearby player");
            check(h, p.getChunkTrackingView() instanceof UavChunkView, "vanilla tick retains camera view");
            check(h, p.position().distanceTo(origin) < 2, "operator remains at console");
            s.disconnect(); check(h, s.remoteTicketCount() == 0 && p.getChunkTrackingView() instanceof ChunkTrackingView.Positioned, "exit releases tickets and restores body terrain");
            for (var pos : cameraChunks) {
                check(h, !UavRemoteSession.TICKETS.forceChunk(h.getLevel(), s.getUUID(), pos.x(), pos.z(), false, false), "actual NeoForge ticket already removed");
            }
            check(h, UavRemoteSession.trackingDelta(ordinary, ac, p).equals(ordinary), "exit restores ordinary entity tracking");
            clean(ac, p, s); h.succeed();
        });
    }
    private static void probe(GameTestHelper h) {
        var s = station(h, false); var p = operator(h, s); var ac = deploy(h, s, p, "rc-goblin");
        var id = ac.getUUID(); ac.setPos(s.position().add(4500, 20, 0)); UavPositionIndex.update(ac);
        ac.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        check(h, UavPositionIndex.get(h.getLevel()).position(id) != null, "unloading preserves saved position");
        check(h, !s.connect(p, java.util.UUID.randomUUID()) && !s.feedback().contains("Loading"), "unpaired ID cannot load arbitrary chunks");
        check(h, !s.connect(p, id) && s.feedback().contains("Loading"), "paired unloaded UAV starts bounded lookup");
        p.stopRiding(); s.tick(); p.startRiding(s, true, true); s.positionRider(p);
        var pos = UavPositionIndex.get(h.getLevel()).position(id);
        check(h, !UavRemoteSession.TICKETS.forceChunk(h.getLevel(), s.getUUID(), pos.getX() >> 4, pos.getZ() >> 4, false, false), "operator loss removed actual probe ticket");
        h.runAfterDelay(5, () -> {
            check(h, s.controlled() == null, "leaving cancels pending reconnect");
            UavPositionIndex.get(h.getLevel()).remove(id); clean(p, s); h.succeed();
        });
    }
    private static void reconnect(GameTestHelper h) {
        var s = station(h, false); var p = h.makeMockServerPlayerInLevel(); p.setGameMode(GameType.SURVIVAL);
        p.setPos(s.position()); p.startRiding(s, true, true); s.positionRider(p);
        var ac = deploy(h, s, p, "rc-goblin");
        ac.setPos(s.position().add(4600, 20, 0)); UavPositionIndex.update(ac);
        var tag = save(h, ac); var id = ac.getUUID(); var pos = ac.position();
        ac.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        check(h, !s.connect(p, id) && s.feedback().contains("Loading"), "unloaded selection starts lookup");
        // Simulate the saved entity arriving asynchronously from the now-requested chunk.
        var loaded = ((io.github.mcheliceport.item.AircraftItem) item("rc-goblin").getItem())
                .createAircraft(h.getLevel(), pos.x, pos.y, pos.z);
        loaded.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
                h.getLevel().registryAccess(), tag));
        h.runAfterDelay(5, () -> check(h, h.getLevel().addFreshEntity(loaded), "saved UAV arrives"));
        // The batch's chunks may not tick entities yet, so tick the station until the lookup finds the UAV.
        h.runAfterDelay(20, () -> h.succeedWhen(() -> {
            if (s.controlled() != loaded) { s.tick(); }
            check(h, s.controlled() == loaded && loaded.getUUID().equals(id) && loaded.getPilot() == p,
                    "UUID and ownership validated before reconnect: " + s.feedback());
            clean(loaded, p, s);
        }));
    }
}
