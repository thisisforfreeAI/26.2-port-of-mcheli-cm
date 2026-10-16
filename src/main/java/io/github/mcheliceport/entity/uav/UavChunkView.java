package io.github.mcheliceport.entity.uav;

import java.util.function.Consumer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;

/** Two small areas, never the corridor between the operator and the camera. */
public record UavChunkView(ChunkTrackingView.Positioned body, ChunkTrackingView.Positioned camera) implements ChunkTrackingView {
    @Override public boolean contains(int x, int z, boolean neighbors) {
        return body.contains(x, z, neighbors) || camera.contains(x, z, neighbors);
    }
    @Override public void forEach(Consumer<ChunkPos> consumer) {
        body.forEach(consumer);
        camera.forEach(pos -> { if (!body.contains(pos)) { consumer.accept(pos); } });
    }
    public void difference(ChunkTrackingView previous, Consumer<ChunkPos> enter, Consumer<ChunkPos> leave) {
        previous.forEach(pos -> { if (!contains(pos)) { leave.accept(pos); } });
        forEach(pos -> { if (!previous.contains(pos)) { enter.accept(pos); } });
    }
}
