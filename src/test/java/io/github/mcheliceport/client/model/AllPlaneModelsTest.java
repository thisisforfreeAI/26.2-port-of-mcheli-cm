package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentTestSupport;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

/**
 * Exit criterion E6: every stock plane's body model parses, every part it draws is in the
 * body or in its own file, and every texture it can use exists.
 */
class AllPlaneModelsTest {

    /**
     * Parts the stock content names but upstream's own model files never had (upstream draws
     * nothing for them, as the port does). Listed so that any other gap fails.
     */
    private static List<String> upstreamGaps() throws java.io.IOException {
        try (var stream = AllPlaneModelsTest.class.getResourceAsStream("/parity/plane-model-gaps.txt")) {
            assertTrue(stream != null, "upstream model gap fixture exists");
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
        }
    }

    @Test
    void everyStockPlaneHasItsModelsAndTextures() throws java.io.IOException {
        ContentRegistries.Snapshot stock = ContentTestSupport.load(ContentTestSupport.stockPack());
        PathPackResources pack = new PathPackResources(new PackLocationInfo("mcheli_test", Component.empty(),
                PackSource.DEFAULT, Optional.empty()), ContentTestSupport.stockPack());
        List<String> problems = new ArrayList<>();
        int parts = 0;
        try (MultiPackResourceManager resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
                List.of(pack))) {
            for (PlaneInfo info : stock.plane().values()) {
                AircraftModels.VehicleModels models = AircraftModels.load(resources, "planes", info);
                if (models == null) {
                    problems.add(info.name + ": no body model");
                    continue;
                }
                for (String part : AircraftModels.partNames(info)) {
                    parts++;
                    if (CanopyAlias.missing(models::part, part)) {
                        problems.add(info.name + ": part " + part + " is neither in the body nor in a file");
                    }
                }
                for (String texture : info.getTextureNames()) {
                    checkTexture(resources, info.name, texture, problems);
                }
            }
        }
        assertEquals(upstreamGaps(), problems);
        assertEquals(46, stock.plane().size(), "stock planes");
        assertTrue(parts > 100, "parts checked: " + parts);
    }

    private static void checkTexture(ResourceManager resources, String heli, String texture, List<String> problems) {
        String path = "textures/planes/" + texture + ".png";
        if (!Identifier.isValidPath(path)) {
            problems.add(heli + ": texture name " + texture + " is not a valid resource path");
            return;
        }
        if (resources.getResource(Identifier.fromNamespaceAndPath("mcheli", path)).isEmpty()) {
            problems.add(heli + ": texture " + path.toLowerCase(Locale.ROOT) + " is missing");
        }
    }
}
