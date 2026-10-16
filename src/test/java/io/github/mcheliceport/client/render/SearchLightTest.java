package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.ContentTestSupport;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.entity.aircraft.SearchLightBeam;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class SearchLightTest {
    @Test void renderPipelineRegistersWithAnExplicitNamespace() {
        SearchLightRenderer.registerPipelines(new net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent(
                pipeline -> assertEquals(net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        "mcheli_ce_port", "pipeline/searchlight"), pipeline.getLocation())));
    }
    private static AircraftInfo.SearchLight lamp(boolean fixed, boolean steering, float yaw, float pitch, float factor) {
        return new AircraftInfo.SearchLight(new Vec3(2, 1, -3), 0x50FFFFFF, 0x10FFFFC0, 60, 20,
                fixed, yaw, pitch, steering, factor);
    }
    @Test void coneFramesMatchUpstreamGlCallsAtNonzeroAttitudes() {
        for (boolean fixed : new boolean[] {false, true}) {
            for (boolean steering : new boolean[] {false, true}) {
                var light = lamp(fixed, steering, 12, -8, 35);
                var beam = SearchLightBeam.of(light, -120, 24, -17, 95, 32, -0.6F);
                var gl = new GlMatrixStack(); gl.translate(0, AircraftInfo.GLOBAL_Y_OFFSET, 0);
                gl.rotate(-120, 0, -1, 0); gl.rotate(24, 1, 0, 0); gl.rotate(-17, 0, 0, 1);
                gl.translate(2, 1, -3);
                gl.rotate(fixed ? 12 + (steering ? 21 : 0) : 95 + 120 + 12, 0, -1, 0);
                gl.rotate(fixed ? 82 : 32 + 90 - 24 - 8, 1, 0, 0);
                for (var point : List.of(Vec3.ZERO, new Vec3(0, 60, 0), new Vec3(10, 60, 0), new Vec3(0, 60, 10))) {
                    var expected = gl.current().transformPosition((float) point.x, (float) point.y, (float) point.z, new Vector3f());
                    assertEquals(0, beam.point((float) point.x, (float) point.y, (float) point.z)
                            .distanceTo(new Vec3(expected.x, expected.y, expected.z)), 0.0001);
                }
            }
        }
    }
    @Test void movableLampFollowsLookWhileFixedLampIgnoresIt() {
        var moving = SearchLightBeam.of(lamp(false, false, 0, 0, 0), 0, 0, 0, -90, 30, 0);
        var axis = moving.point(0, 1, 0).subtract(moving.point(0, 0, 0));
        assertEquals(Math.sqrt(3) / 2, axis.x, 0.00001); assertEquals(-0.5, axis.y, 0.00001); assertEquals(0, axis.z, 0.00001);
        var fixed = SearchLightBeam.of(lamp(true, false, 0, 0, 0), 0, 0, 0, -90, 30, 0);
        assertEquals(1, fixed.point(0, 1, 0).subtract(fixed.point(0, 0, 0)).z, 0.00001);
    }
    @Test void lightingRejectsOutsideRangeAndBehindAndUsesTurnedCone() {
        var beam = SearchLightBeam.of(lamp(false, false, 0, 0, 0), 80, 0, 0, -90, 20, 0);
        assertEquals(15, beam.blockLight(beam.point(0, 20, 0)));
        assertEquals(0, beam.blockLight(beam.point(0, -20, 0)));
        assertEquals(0, beam.blockLight(beam.point(0, 70, 0)));
        assertEquals(0, beam.blockLight(beam.point(50, 20, 0)));
        assertEquals(0, beam.blockLight(beam.point(0, 0, 0)));
    }
    @Test void stockLampAlphaMatchesLegacy() {
        var stock = ContentTestSupport.load(ContentTestSupport.stockPack());
        var legacy = ContentTestSupport.load(ContentTestSupport.legacyPack());
        int count = 0;
        for (var info : stock.aircraft()) {
            if (info.searchLights.isEmpty()) { continue; }
            var old = legacy.aircraft().stream().filter(i -> i.name.equals(info.name)).findFirst().orElseThrow();
            for (int i = 0; i < info.searchLights.size(); i++) {
                assertEquals(old.searchLights.get(i).colorStart, info.searchLights.get(i).colorStart);
                assertEquals(old.searchLights.get(i).colorEnd, info.searchLights.get(i).colorEnd); count++;
            }
        }
        assertEquals(15, count);
    }
    @Test void addonLightCoverRotatesAroundItsPivot() {
        var info = new HeliInfo(ContentLocation.builtin("cover"), "cover.yml", "yaml");
        info.lightHatchList.add(new AircraftInfo.Hatch(new Vec3(2, 1, 3), new Vec3(1, 0, 0), "light0", 90, false));
        var body = new McheliModel("body", List.of(new McheliModel.Group("$light0", new float[0])), 0, 0, 0, 0, 0, 0);
        var models = new AircraftModels.VehicleModels(body, Map.of());
        for (float fraction : new float[] {0, 0.5F, 1}) {
            var draws = new ArrayList<AircraftPartPoses.Draw>();
            AircraftPartPoses.lightHatches(info, models, fraction, new GlMatrixStack(), draws);
            assertEquals(1, draws.size());
            var pivot = draws.getFirst().pose().transformPosition(2, 1, 3, new Vector3f());
            assertEquals(2, pivot.x, 0.00001); assertEquals(1, pivot.y, 0.00001); assertEquals(3, pivot.z, 0.00001);
            var direction = draws.getFirst().pose().transformDirection(0, 1, 0, new Vector3f());
            assertEquals(Math.cos(Math.toRadians(90 * fraction)), direction.y, 0.00001);
            assertEquals(Math.sin(Math.toRadians(90 * fraction)), direction.z, 0.00001);
        }
    }
}
