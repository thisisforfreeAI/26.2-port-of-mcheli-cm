package io.github.mcheliceport.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Loads all stock content from the YAML files that ship in the mod and from upstream's legacy TXT
 * versions of the same files (test fixtures), and compares the two definition by definition.
 *
 * <p>The two sets do not match exactly. Upstream converted its stock content from TXT to YAML with
 * a tool that rounded numbers to three decimals and dropped some settings (for example paint
 * variants, entity rack yaw, damage factors, bomblets and dispenser items), and later edited the
 * YAML. Those differences are listed in {@code parity/known-differences.txt}; the test fails when
 * the comparison finds anything else, or no longer finds a listed difference, so every change in
 * how either format parses shows up here. Two format quirks are normalised rather than listed:
 * <ul>
 *   <li>numbers are compared to 0.001, the converter's rounding;</li>
 *   <li>TXT camera positions sit {@value #TXT_CAMERA_OFFSET} higher than the same YAML positions,
 *       because upstream's TXT path adds its camera offset twice (see
 *       {@code AircraftInfo.CameraPosition}).</li>
 * </ul>
 * To regenerate the list after a deliberate change, run the tests with
 * {@code -Dmcheli.updateParity=true} and review the diff.
 */
public class ContentParityTest {

    static final double TXT_CAMERA_OFFSET = 0.35;
    private static final String KNOWN_DIFFERENCES = "src/test/resources/parity/known-differences.txt";

    /** The port's NTM Next bombs (weapons/ntm_*.yml), which upstream did not ship. */
    public static final int NTM_BOMBS = 14;

    /** The port's own stock vehicles (M10c's ammo cart), which upstream did not ship. */
    public static final int PORT_VEHICLES = 1;

    /** Stock content counts, from PORTING_PLAN.md section 3.3. */
    private static final Map<String, Integer> EXPECTED_COUNTS = Map.of("helicopters", 34, "planes", 46, "ships", 5,
            "tanks", 12, "vehicles", 18, "weapons", 234, "throwable", 4, "hud", 19);

    /**
     * Fields that legitimately differ between formats: bookkeeping, and crawler track links
     * ({@code CrawlerTrack.lp}), which upstream computes with a different algorithm for each
     * format. The track outline the links come from is still compared.
     */
    private static final Set<String> IGNORED = Set.of("location", "filePath", "parserIdentifier", "fileLine",
            "fileName", "lastWeaponType", "lastWeaponIndex", "lastWeaponPart", "isWaitEndif", "CrawlerTrack.lp");

    private static ContentRegistries.Snapshot txt;
    private static ContentRegistries.Snapshot yml;

    @BeforeAll
    static void load() {
        txt = ContentTestSupport.load(ContentTestSupport.legacyPack());
        yml = ContentTestSupport.load(ContentTestSupport.stockPack());
    }

    @Test
    void everyStockDefinitionLoadsFromTxt() {
        assertCounts(txt, 0, 0);
    }

    @Test
    void everyStockDefinitionLoadsFromYaml() {
        assertCounts(yml, NTM_BOMBS, PORT_VEHICLES);
    }

    @Test
    void txtAndYamlDifferOnlyAsKnown() throws IOException {
        List<String> differences = new ArrayList<>();
        compare(differences, txt.hud(), yml.hud());
        compare(differences, txt.weapon(), yml.weapon());
        compare(differences, txt.heli(), yml.heli());
        compare(differences, txt.plane(), yml.plane());
        compare(differences, txt.ship(), yml.ship());
        compare(differences, txt.tank(), yml.tank());
        compare(differences, txt.vehicle(), yml.vehicle());
        compare(differences, txt.throwable(), yml.throwable());

        Path knownFile = ContentTestSupport.projectDir().resolve(KNOWN_DIFFERENCES);
        if (Boolean.getBoolean("mcheli.updateParity")) {
            // Keep the explanatory header, replace the list.
            List<String> content = new ArrayList<>();
            if (Files.exists(knownFile)) {
                Files.readAllLines(knownFile, StandardCharsets.UTF_8).stream()
                        .takeWhile(line -> line.startsWith("#") || line.isBlank()).forEach(content::add);
            }
            content.addAll(differences);
            Files.createDirectories(knownFile.getParent());
            Files.write(knownFile, content, StandardCharsets.UTF_8);
        }
        List<String> known = Files.readAllLines(knownFile, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
        List<String> unexpected = differences.stream().filter(d -> !known.contains(d)).toList();
        List<String> missing = known.stream().filter(d -> !differences.contains(d)).toList();
        assertTrue(unexpected.isEmpty() && missing.isEmpty(),
                "TXT/YAML parity changed.\nNew differences (" + unexpected.size() + "):\n"
                        + String.join("\n", unexpected) + "\nDifferences no longer found (" + missing.size()
                        + "):\n" + String.join("\n", missing));
    }

    private static <T extends BaseInfo> void compare(List<String> out, ContentRegistry<T> a, ContentRegistry<T> b) {
        Map<String, T> left = new TreeMap<>();
        a.entries().forEach(e -> left.put(e.getKey(), e.getValue()));
        Map<String, T> right = new TreeMap<>();
        b.entries().forEach(e -> right.put(e.getKey(), e.getValue()));
        for (String name : left.keySet()) {
            if (!right.containsKey(name)) {
                out.add(a.getDirectoryName() + "/" + name + ": only in TXT");
            }
        }
        for (String name : right.keySet()) {
            if (!left.containsKey(name)) {
                out.add(a.getDirectoryName() + "/" + name + ": only in YAML");
                continue;
            }
            DeepCompare cmp = new DeepCompare(IGNORED, 1e-3)
                    .withLeftOffset(".*\\.cameraPosition\\[\\d+]\\.pos\\.y", -TXT_CAMERA_OFFSET)
                    .withLeftOffset(".*\\.seatList\\[\\d+]\\.camPos\\.pos\\.y", -TXT_CAMERA_OFFSET);
            cmp.compare(a.getDirectoryName() + "/" + name, left.get(name), right.get(name));
            out.addAll(cmp.differences());
        }
    }

    /** {@code extraWeapons}, {@code extraVehicles}: the port's own stock content, which only the YAML pack has. */
    private static void assertCounts(ContentRegistries.Snapshot s, int extraWeapons, int extraVehicles) {
        Map<String, Integer> actual = new TreeMap<>();
        actual.put("helicopters", s.heli().size());
        actual.put("planes", s.plane().size());
        actual.put("ships", s.ship().size());
        actual.put("tanks", s.tank().size());
        actual.put("vehicles", s.vehicle().size() - extraVehicles);
        actual.put("weapons", s.weapon().size() - extraWeapons);
        actual.put("throwable", s.throwable().size());
        actual.put("hud", s.hud().size());
        assertEquals(new TreeMap<>(EXPECTED_COUNTS), actual);
    }
}
