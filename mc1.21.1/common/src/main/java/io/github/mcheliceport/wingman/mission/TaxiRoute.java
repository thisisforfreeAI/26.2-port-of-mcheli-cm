package io.github.mcheliceport.wingman.mission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Upstream's {@code TaxiRoute}: from a parking spot through ordered waypoints to the runway end
 * where take-off starts, and back after landing.
 *
 * @param runwayId the runway end A (or, for a return-only route, end B, or a helipad) the taxi ends at
 * @param runwayBId the runway end B where take-off ends; empty to search for it
 * @param waypointIds the waypoints in departure order
 * @param arrivalWaypointIds the waypoints after landing; empty for the departure waypoints reversed
 * @param parkingHeading -1 (any), 0 (north), 1 (east), 2 (south) or 3 (west)
 * @param arrivalRunwayId the end the landing aims for; empty for {@code runwayId}
 */
public record TaxiRoute(String routeId, String baseId, String parkingId, String runwayId, String runwayBId,
        List<String> waypointIds, List<String> arrivalWaypointIds, int parkingHeading, String arrivalRunwayId) {

    private static final Codec<List<String>> IDS = Codec.STRING.listOf();

    /** Upstream's saved fields; the optional ones default as upstream read older saves. */
    public static final Codec<TaxiRoute> CODEC = RecordCodecBuilder.create(b -> b.group(
            Codec.STRING.fieldOf("routeId").forGetter(TaxiRoute::routeId),
            Codec.STRING.fieldOf("baseId").forGetter(TaxiRoute::baseId),
            Codec.STRING.fieldOf("parkingId").forGetter(TaxiRoute::parkingId),
            Codec.STRING.fieldOf("runwayId").forGetter(TaxiRoute::runwayId),
            Codec.STRING.optionalFieldOf("runwayBId", "").forGetter(TaxiRoute::runwayBId),
            IDS.optionalFieldOf("waypoints", List.of()).forGetter(TaxiRoute::waypointIds),
            IDS.optionalFieldOf("arrivalWaypoints", List.of()).forGetter(TaxiRoute::arrivalWaypointIds),
            Codec.INT.optionalFieldOf("parkingHeading", -1).forGetter(TaxiRoute::parkingHeading),
            Codec.STRING.optionalFieldOf("arrivalRunwayId", "").forGetter(TaxiRoute::arrivalRunwayId))
            .apply(b, TaxiRoute::new));

    private static final StreamCodec<ByteBuf, String> ID = ByteBufCodecs.stringUtf8(64);
    private static final StreamCodec<ByteBuf, List<String>> ID_LIST = ID.apply(ByteBufCodecs.list(99));

    public static final StreamCodec<ByteBuf, TaxiRoute> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TaxiRoute decode(ByteBuf buf) {
            return new TaxiRoute(ID.decode(buf), ID.decode(buf), ID.decode(buf), ID.decode(buf), ID.decode(buf),
                    ID_LIST.decode(buf), ID_LIST.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ID.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, TaxiRoute r) {
            ID.encode(buf, r.routeId());
            ID.encode(buf, r.baseId());
            ID.encode(buf, r.parkingId());
            ID.encode(buf, r.runwayId());
            ID.encode(buf, r.runwayBId());
            ID_LIST.encode(buf, r.waypointIds());
            ID_LIST.encode(buf, r.arrivalWaypointIds());
            ByteBufCodecs.VAR_INT.encode(buf, r.parkingHeading());
            ID.encode(buf, r.arrivalRunwayId());
        }
    };

    public TaxiRoute {
        waypointIds = List.copyOf(waypointIds);
        arrivalWaypointIds = List.copyOf(arrivalWaypointIds);
    }

    /** The same route under another base, which the server takes from the base marker. */
    public TaxiRoute withBase(String base) {
        return new TaxiRoute(this.routeId, base, this.parkingId, this.runwayId, this.runwayBId, this.waypointIds,
                this.arrivalWaypointIds, this.parkingHeading, this.arrivalRunwayId);
    }

    public List<String> waypointIdsReversed() {
        List<String> reversed = new ArrayList<>(this.waypointIds);
        Collections.reverse(reversed);
        return reversed;
    }

    /** Parking, waypoints, runway end. */
    public List<String> fullDeparture() {
        List<String> all = new ArrayList<>();
        all.add(this.parkingId);
        all.addAll(this.waypointIds);
        all.add(this.runwayId);
        return all;
    }

    /** Landing end, arrival waypoints (or the departure ones reversed), parking. */
    public List<String> fullArrival() {
        List<String> all = new ArrayList<>();
        all.add(this.effectiveArrivalEntry());
        all.addAll(this.arrivalWaypointIds.isEmpty() ? this.waypointIdsReversed() : this.arrivalWaypointIds);
        all.add(this.parkingId);
        return all;
    }

    public String effectiveArrivalEntry() {
        return this.arrivalRunwayId.isEmpty() ? this.runwayId : this.arrivalRunwayId;
    }
}
