package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import com.mojang.serialization.JsonOps;
import io.github.mcheliceport.net.MarkerUpdatePayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.OpenBaseScreenPayload;
import io.github.mcheliceport.net.OpenMarkerScreenPayload;
import io.github.mcheliceport.net.TaxiRouteActionPayload;
import io.github.mcheliceport.registry.ModBlocks;
import io.github.mcheliceport.wingman.block.MarkerType;
import io.github.mcheliceport.wingman.block.WingmanMarkerBlock;
import io.github.mcheliceport.wingman.block.WingmanMarkerBlockEntity;
import io.github.mcheliceport.wingman.mission.TaxiRoute;
import io.github.mcheliceport.wingman.registry.MarkerRegistry;
import io.github.mcheliceport.wingman.registry.TaxiRouteRegistry;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Upstream's wingman markers: the block entity, {@code MarkerRegistry} and {@code PacketMarkerUpdate}. */
final class WingmanMarkerGameTests {

    private WingmanMarkerGameTests() {
    }

    static void register() {
        ModGameTests.test("wingman_marker_takes_an_id_and_registers", 20, WingmanMarkerGameTests::placement);
        ModGameTests.test("wingman_marker_update_applies_within_reach", 20, WingmanMarkerGameTests::update);
        ModGameTests.test("wingman_marker_registry_resolves_bases", 20, WingmanMarkerGameTests::resolve);
        ModGameTests.test("wingman_base_screen_saves_taxi_routes", 20, WingmanMarkerGameTests::taxiRoutes);
        ModGameTests.test("wingman_marker_ids_stay_unique", 20, WingmanMarkerGameTests::uniqueIds);
    }

    private static MarkerRegistry.@Nullable MarkerInfo recorded(ServerLevel level, BlockPos pos) {
        for (MarkerRegistry.MarkerInfo m : MarkerRegistry.snapshot(level)) {
            if (m.pos().equals(pos)) {
                return m;
            }
        }
        return null;
    }

    /** A new marker is a parking spot with a {@code p_} ID in the registry, and leaves it when broken. */
    private static void placement(GameTestHelper h) {
        BlockPos rel = new BlockPos(2, 1, 2);
        BlockPos pos = h.absolutePos(rel);
        h.setBlock(rel, ModBlocks.WINGMAN_MARKER.get().defaultBlockState());
        h.runAfterDelay(2, () -> {
            check(h, h.getBlockState(rel).getValue(WingmanMarkerBlock.TYPE) == MarkerType.PARKING,
                    "a new marker is not a parking spot");
            WingmanMarkerBlockEntity marker = h.getBlockEntity(rel, WingmanMarkerBlockEntity.class);
            check(h, marker.getMarkerId().startsWith("p_"), "the marker took no parking ID: " + marker.getMarkerId());
            MarkerRegistry.MarkerInfo info = recorded(h.getLevel(), pos);
            check(h, info != null && info.id().equals(marker.getMarkerId()), "the marker is not registered");
            h.setBlock(rel, Blocks.AIR);
            check(h, recorded(h.getLevel(), pos) == null, "the broken marker is still registered");
            // The /setblock command skips the block's removal hooks.
            h.getLevel().setBlock(pos, ModBlocks.WINGMAN_MARKER.get().defaultBlockState(), 3);
            if (h.getLevel().getBlockEntity(pos) instanceof WingmanMarkerBlockEntity again) {
                MarkerRegistry.register(h.getLevel(), again.info());
            }
            check(h, recorded(h.getLevel(), pos) != null, "the second marker is not registered");
            h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS
                    | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
            check(h, recorded(h.getLevel(), pos) == null, "a marker replaced as /setblock does is still registered");
            h.succeed();
        });
    }

    /**
     * The screen's settings apply from within reach: a base drops its parent base, only a parking
     * spot keeps a heading, and the type goes to the block state. Out of reach nothing changes.
     */
    private static void update(GameTestHelper h) {
        BlockPos rel = new BlockPos(2, 1, 2);
        BlockPos pos = h.absolutePos(rel);
        h.setBlock(rel, ModBlocks.WINGMAN_MARKER.get().defaultBlockState());
        Player near = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(3.5, 1, 3.5));
        Player far = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(3.5, 1, 40.5));
        h.runAfterDelay(2, () -> {
            WingmanMarkerBlockEntity marker = h.getBlockEntity(rel, WingmanMarkerBlockEntity.class);
            check(h, !marker.getMarkerId().isEmpty(), "the marker has no ID yet");
            ModNetwork.applyMarkerUpdate(far, new MarkerUpdatePayload(pos, MarkerType.RUNWAY_A, "far", "x", 1));
            check(h, !marker.getMarkerId().equals("far")
                    && marker.getMarkerType() == MarkerType.PARKING, "an update applied out of reach");

            ModNetwork.applyMarkerUpdate(near, new MarkerUpdatePayload(pos, MarkerType.RUNWAY_A, " ra_alpha ", "alpha", 2));
            check(h, h.getBlockState(rel).getValue(WingmanMarkerBlock.TYPE) == MarkerType.RUNWAY_A,
                    "the block state kept the old type");
            check(h, marker.getMarkerId().equals("ra_alpha") && marker.getBaseId().equals("alpha")
                    && marker.getParkingHeading() == -1, "the runway end's settings are wrong");
            MarkerRegistry.MarkerInfo info = recorded(h.getLevel(), pos);
            check(h, info != null && info.type() == MarkerType.RUNWAY_A && info.baseId().equals("alpha"),
                    "the registry kept the old settings");

            ModNetwork.applyMarkerUpdate(near, new MarkerUpdatePayload(pos, MarkerType.PARKING, "p_alpha", "alpha", 3));
            check(h, marker.getParkingHeading() == 3, "the parking spot lost its heading");
            ModNetwork.applyMarkerUpdate(near, new MarkerUpdatePayload(pos, MarkerType.BASE, "alpha", "beta", 3));
            check(h, marker.getBaseId().isEmpty() && marker.getParkingHeading() == -1,
                    "the base kept a parent base or heading");

            var buf = new FriendlyByteBuf(Unpooled.buffer());
            var sent = new MarkerUpdatePayload(pos, MarkerType.HELIPAD_B, "hpb_1", "alpha", -1);
            MarkerUpdatePayload.CODEC.encode(buf, sent);
            check(h, MarkerUpdatePayload.CODEC.decode(buf).equals(sent), "the payload changed in its codec");
            h.setBlock(rel, Blocks.AIR);
            h.succeed();
        });
    }

    /** Upstream {@code resolveMarker}: a base's child of the type, else a marker with that ID; the save keeps both. */
    private static void resolve(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos base = h.absolutePos(new BlockPos(1, 1, 1));
        BlockPos runway = h.absolutePos(new BlockPos(1, 1, 3));
        BlockPos lone = h.absolutePos(new BlockPos(1, 1, 5));
        MarkerRegistry.register(level, new MarkerRegistry.MarkerInfo(base, MarkerType.BASE, "resolve_base", "", -1));
        MarkerRegistry.register(level, new MarkerRegistry.MarkerInfo(runway, MarkerType.RUNWAY_A, "ra_9", "resolve_base", -1));
        MarkerRegistry.register(level, new MarkerRegistry.MarkerInfo(lone, MarkerType.RUNWAY_A, "resolve_lone", "", -1));
        MarkerRegistry.MarkerInfo child = MarkerRegistry.resolveMarker(level, "resolve_base", MarkerType.RUNWAY_A);
        check(h, child != null && child.pos().equals(runway), "the base did not resolve to its runway end");
        MarkerRegistry.MarkerInfo direct = MarkerRegistry.resolveMarker(level, "resolve_lone", MarkerType.RUNWAY_A);
        check(h, direct != null && direct.pos().equals(lone), "the ID did not resolve to its marker");
        check(h, MarkerRegistry.findChildren(level, "resolve_base").size() == 1, "the base has the wrong children");

        var markers = List.of(MarkerRegistry.findById(level, MarkerType.BASE, "resolve_base"), child);
        var json = MarkerRegistry.MarkerInfo.CODEC.listOf().encodeStart(JsonOps.INSTANCE, markers).getOrThrow();
        check(h, MarkerRegistry.MarkerInfo.CODEC.listOf().parse(JsonOps.INSTANCE, json).getOrThrow().equals(markers),
                "the saved markers changed");
        for (BlockPos pos : List.of(base, runway, lone)) {
            MarkerRegistry.unregister(level, pos);
        }
        h.succeed();
    }

    private static WingmanMarkerBlockEntity marker(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, ModBlocks.WINGMAN_MARKER.get().defaultBlockState());
        return h.getBlockEntity(rel, WingmanMarkerBlockEntity.class);
    }

    /**
     * Upstream {@code openBaseGui} and {@code PacketBaseAction}'s route actions: the base screen lists
     * the base's markers, a saved route goes under the marker's base whatever the client sent, a
     * route needs its parking spot and runway end, and a delete only reaches the base's own routes.
     */
    private static void taxiRoutes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos baseRel = new BlockPos(1, 1, 1);
        WingmanMarkerBlockEntity base = marker(h, baseRel);
        WingmanMarkerBlockEntity parking = marker(h, new BlockPos(1, 1, 3));
        WingmanMarkerBlockEntity runway = marker(h, new BlockPos(1, 1, 5));
        WingmanMarkerBlockEntity waypoint = marker(h, new BlockPos(3, 1, 5));
        Player near = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(2.5, 1, 2.5));
        Player far = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(2.5, 1, 40.5));
        BlockPos pos = h.absolutePos(baseRel);
        h.runAfterDelay(2, () -> {
            base.apply(MarkerType.BASE, "taxi_base", "", -1);
            parking.apply(MarkerType.PARKING, "taxi_p", "taxi_base", 1);
            runway.apply(MarkerType.RUNWAY_A, "taxi_ra", "taxi_base", -1);
            // A waypoint without its base still shows within 500 blocks.
            waypoint.apply(MarkerType.WAYPOINT, "taxi_wp", "", -1);
            var screen = WingmanMarkerBlock.baseScreen(level, pos, "taxi_base");
            check(h, screen.parking().size() == 1 && screen.parking().get(0).id().equals("taxi_p")
                    && screen.runwayA().size() == 1
                    && screen.waypoints().stream().anyMatch(m -> m.id().equals("taxi_wp")),
                    "the base screen lists the wrong markers");

            var route = new TaxiRoute("taxi_route", "spoofed", "taxi_p", "taxi_ra", "", List.of("taxi_wp"), List.of(),
                    -1, "");
            ModNetwork.applyTaxiRouteAction(far, new TaxiRouteActionPayload(pos, false, route));
            check(h, TaxiRouteRegistry.findById(level, "taxi_route") == null, "a route saved out of reach");
            ModNetwork.applyTaxiRouteAction(near, new TaxiRouteActionPayload(pos, false, route));
            TaxiRoute saved = TaxiRouteRegistry.findById(level, "taxi_route");
            check(h, saved != null && saved.baseId().equals("taxi_base") && saved.waypointIds().equals(List.of("taxi_wp")),
                    "the route was not saved under the marker's base");
            check(h, saved.fullDeparture().equals(List.of("taxi_p", "taxi_wp", "taxi_ra"))
                    && saved.fullArrival().equals(List.of("taxi_ra", "taxi_wp", "taxi_p")), "the route's legs are wrong");
            check(h, WingmanMarkerBlock.baseScreen(level, pos, "taxi_base").routes().equals(List.of(saved)),
                    "the base screen does not list the route");

            var noParking = new TaxiRoute("taxi_bad", "", "", "taxi_ra", "", List.of(), List.of(), -1, "");
            ModNetwork.applyTaxiRouteAction(near, new TaxiRouteActionPayload(pos, false, noParking));
            check(h, TaxiRouteRegistry.findById(level, "taxi_bad") == null, "a route without parking was saved");

            var other = new TaxiRoute("taxi_other", "taxi_elsewhere", "p", "r", "", List.of(), List.of(), -1, "");
            TaxiRouteRegistry.save(level, other);
            ModNetwork.applyTaxiRouteAction(near, new TaxiRouteActionPayload(pos, true, other));
            check(h, TaxiRouteRegistry.findById(level, "taxi_other") != null, "another base's route was deleted");
            ModNetwork.applyTaxiRouteAction(near, new TaxiRouteActionPayload(pos, true, saved));
            check(h, TaxiRouteRegistry.findById(level, "taxi_route") == null, "the route was not deleted");
            TaxiRouteRegistry.delete(level, "taxi_other");

            var json = TaxiRoute.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow();
            check(h, TaxiRoute.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().equals(saved), "the saved route changed");
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            OpenBaseScreenPayload.CODEC.encode(buf, screen);
            check(h, OpenBaseScreenPayload.CODEC.decode(buf).equals(screen), "the base screen changed in its codec");
            for (int x : new int[] {1, 3}) {
                for (int z : new int[] {1, 3, 5}) {
                    h.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                }
            }
            h.succeed();
        });
    }

    /**
     * Upstream counted a type's markers for a new ID, so a parking spot turned helipad kept
     * {@code p_1} and the next parking spot took {@code p_1} again. The port takes the lowest free
     * number, renames an automatic ID of another type, keeps a typed one, and gives the marker
     * screen the bases nearby and the other markers' IDs.
     */
    private static void uniqueIds(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos firstRel = new BlockPos(1, 1, 1);
        BlockPos secondRel = new BlockPos(1, 1, 3);
        BlockPos baseRel = new BlockPos(3, 1, 1);
        WingmanMarkerBlockEntity first = marker(h, firstRel);
        Player near = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(2.5, 1, 2.5));
        h.runAfterDelay(2, () -> {
            String parkingId = first.getMarkerId();
            check(h, parkingId.startsWith("p_"), "the first marker took no parking ID: " + parkingId);
            ModNetwork.applyMarkerUpdate(near, new MarkerUpdatePayload(h.absolutePos(firstRel), MarkerType.HELIPAD,
                    parkingId, "uid_base", -1));
            check(h, first.getMarkerId().startsWith("hp_"), "the helipad kept its parking ID: " + first.getMarkerId());

            WingmanMarkerBlockEntity second = marker(h, secondRel);
            WingmanMarkerBlockEntity base = marker(h, baseRel);
            h.runAfterDelay(2, () -> {
                check(h, !second.getMarkerId().equals(first.getMarkerId()), "two markers share an ID");
                ModNetwork.applyMarkerUpdate(near, new MarkerUpdatePayload(h.absolutePos(secondRel), MarkerType.HELIPAD,
                        "", "uid_base", -1));
                check(h, second.getMarkerId().startsWith("hp_") && !second.getMarkerId().equals(first.getMarkerId()),
                        "the second helipad repeated an ID: " + second.getMarkerId());
                ModNetwork.applyMarkerUpdate(near, new MarkerUpdatePayload(h.absolutePos(secondRel), MarkerType.WAYPOINT,
                        "tower", "uid_base", -1));
                check(h, second.getMarkerId().equals("tower"), "a typed ID was replaced");
                check(h, WingmanMarkerBlock.isAutoId("hpb_12") && !WingmanMarkerBlock.isAutoId("tower")
                        && !WingmanMarkerBlock.isAutoId("p_") && !WingmanMarkerBlock.isAutoId("x_1"),
                        "automatic IDs are recognised wrongly");
                check(h, WingmanMarkerBlock.nextAutoId(MarkerType.PARKING, List.of("p_1", "p_3")).equals("p_2"),
                        "the lowest free number was not taken");

                base.apply(MarkerType.BASE, "uid_base", "", -1);
                BlockPos pos = h.absolutePos(secondRel);
                check(h, WingmanMarkerBlock.nearbyBases(level, pos).contains("uid_base"), "the base is not offered");
                check(h, WingmanMarkerBlock.otherIds(level, pos).contains(first.getMarkerId())
                        && !WingmanMarkerBlock.otherIds(level, pos).contains("tower"), "the other IDs are wrong");
                var buf = new FriendlyByteBuf(Unpooled.buffer());
                var sent = new OpenMarkerScreenPayload(pos, MarkerType.WAYPOINT, "tower", "uid_base", -1,
                        List.of("uid_base"), List.of("hp_1", "p_2"));
                OpenMarkerScreenPayload.CODEC.encode(buf, sent);
                check(h, OpenMarkerScreenPayload.CODEC.decode(buf).equals(sent), "the screen payload changed in its codec");
                for (BlockPos rel : List.of(firstRel, secondRel, baseRel)) {
                    h.setBlock(rel, Blocks.AIR);
                }
                h.succeed();
            });
        });
    }
}
