package io.github.mcheliceport.entity.uav;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.JsonOps;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class UavRangeTest {
    private static Set<ChunkPos> chunks(ChunkTrackingView view) {
        var result = new HashSet<ChunkPos>(); view.forEach(p -> assertTrue(result.add(p), "duplicate chunk")); return result;
    }
    @Test void distantCameraLoadsTwoBoundedAreasWithoutCorridor() {
        var view = new UavChunkView(new ChunkTrackingView.Positioned(new ChunkPos(-100, -100), 1),
                new ChunkTrackingView.Positioned(new ChunkPos(212, -100), 8));
        var all = chunks(view);
        assertTrue(view.contains(-100, -100)); assertTrue(view.contains(212, -100));
        assertFalse(view.contains(50, -100)); assertTrue(all.size() < 500);
        for (var pos : all) { assertTrue(view.contains(pos)); }
    }
    @Test void movingCameraOnlySendsEnteringEdgeAndDropsLeavingEdge() {
        var body = new ChunkTrackingView.Positioned(new ChunkPos(-20, -20), 1);
        var old = new UavChunkView(body, new ChunkTrackingView.Positioned(new ChunkPos(292, -20), 4));
        var next = new UavChunkView(body, new ChunkTrackingView.Positioned(new ChunkPos(293, -20), 4));
        var entered = new HashSet<ChunkPos>(); var left = new HashSet<ChunkPos>(); next.difference(old, entered::add, left::add);
        var expectedEnter = chunks(next); expectedEnter.removeAll(chunks(old));
        var expectedLeave = chunks(old); expectedLeave.removeAll(chunks(next));
        assertEquals(expectedEnter, entered); assertEquals(expectedLeave, left);
        assertFalse(entered.isEmpty()); assertTrue(entered.size() < 20); assertTrue(left.size() < 20);
    }
    @Test void overlappingWindowsNeverSendDuplicateChunks() {
        var body = new ChunkTrackingView.Positioned(new ChunkPos(-1, -1), 1);
        var camera = new ChunkTrackingView.Positioned(new ChunkPos(0, 0), 4);
        var expected = chunks(body); expected.addAll(chunks(camera));
        assertEquals(expected, chunks(new UavChunkView(body, camera)));
    }
    @Test void savedPositionsSurviveReloadAndOnlyDirtyOnChange() {
        var index = new UavPositionIndex(); var id = UUID.randomUUID(); var pos = new BlockPos(-5000, 80, 12);
        index.record(id, pos); assertTrue(index.isDirty()); index.setDirty(false);
        index.record(id, pos); assertFalse(index.isDirty());
        var json = UavPositionIndex.CODEC.encodeStart(JsonOps.INSTANCE, index).getOrThrow();
        var loaded = UavPositionIndex.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(pos, loaded.position(id)); assertNull(loaded.position(UUID.randomUUID()));
        loaded.remove(id); assertNull(loaded.position(id)); assertTrue(loaded.isDirty());
    }
}
