package io.github.mcheliceport.entity.uav;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.net.ModNetwork;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import io.github.mcheliceport.shim.TicketController;

/** Loaded terrain and vanilla chunk/entity delivery for one authorized remote pilot. */
public final class UavRemoteSession {
    public static final TicketController TICKETS = new TicketController(ResourceLocation.fromNamespaceAndPath("mcheli_ce_port", "uav_control"));
    private final UavStationEntity station;
    private final ServerPlayer player;
    private final Set<ChunkPos> tickets = new HashSet<>();
    private ChunkPos center;
    private UavChunkView lastView;
    private final net.minecraft.server.level.ServerLevel level;
    public UavRemoteSession(UavStationEntity station, ServerPlayer player) { this.station = station; this.player = player; this.level = player.serverLevel(); }
    public int ticketCount() { return tickets.size(); }
    public boolean belongsTo(ServerPlayer p) { return player == p; }
    public void update(AircraftEntity ac) {
        int configured = ServerConfig.SPEC.isLoaded() ? ServerConfig.UAV_REMOTE_VIEW_DISTANCE.get() : 4;
        int radius = Math.max(2, Math.min(configured, Math.min(level.getServer().getPlayerList().getViewDistance(), player.requestedViewDistance())));
        var view = new UavChunkView(new ChunkTrackingView.Positioned(player.chunkPosition(), 1),
                new ChunkTrackingView.Positioned(ac.chunkPosition(), radius));
        if (view.equals(player.getChunkTrackingView())) { return; }
        Set<ChunkPos> nextTickets = new HashSet<>(); view.camera().forEach(nextTickets::add);
        // Load the next camera area before releasing the old one, so crossing an edge keeps ticking.
        for (var pos : nextTickets) {
            if (!tickets.contains(pos)) { TICKETS.forceChunk(level, station.getUUID(), pos.x, pos.z, true, false); }
        }
        for (var pos : tickets) {
            if (!nextTickets.contains(pos)) { TICKETS.forceChunk(level, station.getUUID(), pos.x, pos.z, false, false); }
        }
        tickets.clear(); tickets.addAll(nextTickets);
        var previous = player.getChunkTrackingView();
        if (!ac.chunkPosition().equals(center) || !(previous instanceof UavChunkView)) {
            center = ac.chunkPosition(); player.connection.send(new ClientboundSetChunkCacheCenterPacket(center.x, center.z));
        }
        if (!view.equals(previous)) {
            var map = level.getChunkSource().chunkMap;
            var access = (io.github.mcheliceport.mixin.ChunkMapAccessor) map;
            view.difference(previous, pos -> access.mcheli$markChunkPendingToSend(player, pos), pos -> io.github.mcheliceport.mixin.ChunkMapAccessor.mcheli$dropChunk(player, pos));
            player.setChunkTrackingView(view);
        }
        lastView = (UavChunkView) player.getChunkTrackingView();
    }
    public void close() {
        for (var pos : tickets) { TICKETS.forceChunk(level, station.getUUID(), pos.x, pos.z, false, false); }
        tickets.clear();
        if (player.connection != null && player.level() == level && player.getChunkTrackingView() == lastView) {
            var map = player.serverLevel().getChunkSource().chunkMap;
            // The client cache was centered on the drone. Resend the body area even if it overlaps the old union.
            ((io.github.mcheliceport.mixin.ChunkMapAccessor) map).mcheli$applyChunkTrackingView(player, ChunkTrackingView.EMPTY);
            int radius = Math.min(player.level().getServer().getPlayerList().getViewDistance(), player.requestedViewDistance());
            ((io.github.mcheliceport.mixin.ChunkMapAccessor) map).mcheli$applyChunkTrackingView(player, ChunkTrackingView.of(player.chunkPosition(), radius));
        }
    }
    /** Extend entity tracking around the active camera without moving the physical player. */
    public static Vec3 trackingDelta(Vec3 ordinary, Entity entity, ServerPlayer player) {
        if (!(player.getVehicle() instanceof UavStationEntity station) || !station.streamingFor(player)) { return ordinary; }
        AircraftEntity ac = ModNetwork.riddenAircraft(player);
        if (ac == null) { return ordinary; }
        Vec3 camera = ac.position().subtract(entity.position());
        return camera.horizontalDistanceSqr() < ordinary.horizontalDistanceSqr() ? camera : ordinary;
    }
}
