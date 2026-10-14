package io.github.mcheliceport.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.content.aircraft.ShipInfo;
import io.github.mcheliceport.content.aircraft.TankInfo;
import io.github.mcheliceport.content.aircraft.VehicleInfo;
import io.github.mcheliceport.content.emitter.YamlEmitter;
import io.github.mcheliceport.content.hud.Hud;
import io.github.mcheliceport.content.throwable.ThrowableInfo;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Converts the legacy TXT stock content to YAML with {@link YamlEmitter} (what
 * {@code /mcheli dumpyaml} does), parses the result, and checks that nothing was lost.
 */
class YamlRoundTripTest {

    /**
     * Bookkeeping fields, plus values each parser derives with its own formula (as upstream does):
     * crawler track links and the marker and draw bounds that come from the bounding boxes. Their
     * inputs are compared.
     */
    private static final Set<String> IGNORED = Set.of("location", "filePath", "parserIdentifier", "fileLine",
            "fileName", "lastWeaponType", "lastWeaponIndex", "lastWeaponPart", "isWaitEndif", "CrawlerTrack.lp",
            "AircraftInfo.bbZ", "AircraftInfo.bbZmin", "AircraftInfo.bbZmax", "AircraftInfo.markerWidth",
            "AircraftInfo.markerHeight");

    @TempDir
    Path tempDir;

    @Test
    void txtSurvivesConversionToYaml() throws Exception {
        ContentRegistries.Snapshot txt = ContentTestSupport.load(ContentTestSupport.legacyPack());
        YamlEmitter emitter = new YamlEmitter();
        Path pack = this.tempDir.resolve("converted");
        Path assets = pack.resolve("assets/mcheli");
        int written = 0;
        for (Hud hud : txt.hud().values()) {
            write(assets, "hud", hud, emitter.emitHud(hud));
            written++;
        }
        for (WeaponInfo info : txt.weapon().values()) {
            write(assets, "weapons", info, emitter.emitWeapon(info));
            written++;
        }
        for (HeliInfo info : txt.heli().values()) {
            write(assets, "helicopters", info, emitter.emitHelicopter(info));
            written++;
        }
        for (PlaneInfo info : txt.plane().values()) {
            write(assets, "planes", info, emitter.emitPlane(info));
            written++;
        }
        for (ShipInfo info : txt.ship().values()) {
            write(assets, "ships", info, emitter.emitShip(info));
            written++;
        }
        for (TankInfo info : txt.tank().values()) {
            write(assets, "tanks", info, emitter.emitTank(info));
            written++;
        }
        for (VehicleInfo info : txt.vehicle().values()) {
            write(assets, "vehicles", info, emitter.emitVehicle(info));
            written++;
        }
        for (ThrowableInfo info : txt.throwable().values()) {
            write(assets, "throwable", info, emitter.emitThrowable(info));
            written++;
        }
        assertEquals(372, written);

        ContentRegistries.Snapshot yml = ContentTestSupport.load(pack);
        assertEquals(txt.totalSize(), yml.totalSize(), "every converted file loads");

        List<String> differences = new ArrayList<>();
        compare(differences, txt.hud(), yml.hud());
        compare(differences, txt.weapon(), yml.weapon());
        compare(differences, txt.heli(), yml.heli());
        compare(differences, txt.plane(), yml.plane());
        compare(differences, txt.ship(), yml.ship());
        compare(differences, txt.tank(), yml.tank());
        compare(differences, txt.vehicle(), yml.vehicle());
        compare(differences, txt.throwable(), yml.throwable());
        assertTrue(differences.isEmpty(), differences.size() + " differences after conversion:\n"
                + String.join("\n", differences));
    }

    private static void write(Path assets, String dir, BaseInfo info, String yaml) throws IOException {
        YamlEmitter.writeTo(assets.resolve(dir).resolve(info.getName() + ".yml"), yaml);
    }

    private static <T extends BaseInfo> void compare(List<String> out, ContentRegistry<T> a, ContentRegistry<T> b) {
        for (Map.Entry<String, T> e : a.entries()) {
            T other = b.get(e.getKey());
            if (other == null) {
                out.add(a.getDirectoryName() + "/" + e.getKey() + ": missing after conversion");
                continue;
            }
            // The emitter rounds numbers to three decimals.
            DeepCompare cmp = new DeepCompare(IGNORED, 1e-3);
            cmp.compare(a.getDirectoryName() + "/" + e.getKey(), e.getValue(), other);
            out.addAll(cmp.differences());
        }
    }
}
