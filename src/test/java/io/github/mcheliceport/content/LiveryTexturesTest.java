package io.github.mcheliceport.content;

import static org.junit.jupiter.api.Assertions.*;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * Upstream's YAML conversion dropped every {@code AddTexture} line, so the paint schemes that ship
 * as PNGs could not be picked. The stock definitions list them again, in upstream's order (the
 * parity test checks that against the legacy TXT files); this keeps the lists and the PNGs in step.
 */
class LiveryTexturesTest {
    private static final Path TEXTURES = ContentTestSupport.stockPack().resolve("assets/mcheli/textures");
    /** Shipped PNGs upstream never listed: there is no M2 vehicle, and no AddTexture names fuel_truck_2. */
    private static final Set<String> NOT_LIVERIES = Set.of("vehicles/m2", "planes/fuel_truck_2");

    @Test void everyListedLiveryIsAValidShippedTexture() {
        var stock = ContentTestSupport.load(ContentTestSupport.stockPack());
        for (AircraftInfo info : stock.aircraft()) {
            for (String name : info.getTextureNames()) {
                String path = "textures/" + info.getDirectoryName() + "/" + name + ".png";
                assertNotNull(Identifier.tryParse("mcheli:" + path), info.name + " livery " + name);
                assertTrue(Files.isRegularFile(ContentTestSupport.stockPack().resolve("assets/mcheli/" + path)),
                        info.name + " livery " + name);
            }
            assertEquals(new HashSet<>(info.getTextureNames()).size(), info.getTextureNames().size(), info.name);
        }
    }

    @Test void everyShippedPaintSchemeIsSelectable() throws IOException {
        var stock = ContentTestSupport.load(ContentTestSupport.stockPack());
        Set<String> listed = new HashSet<>();
        for (AircraftInfo info : stock.aircraft()) {
            info.getTextureNames().forEach(n -> listed.add(info.getDirectoryName() + "/" + n));
        }
        Set<String> unused = new TreeSet<>();
        for (String dir : List.of("helicopters", "planes", "ships", "tanks", "vehicles")) {
            try (Stream<Path> files = Files.list(TEXTURES.resolve(dir))) {
                files.map(f -> f.getFileName().toString()).filter(f -> f.endsWith(".png"))
                        .map(f -> dir + "/" + f.substring(0, f.length() - 4))
                        .filter(t -> !listed.contains(t) && !NOT_LIVERIES.contains(t)).forEach(unused::add);
            }
        }
        assertEquals(Set.of(), unused, "paint schemes no vehicle lists");
        assertTrue(listed.size() >= 250, "liveries: " + listed.size());
    }
}
