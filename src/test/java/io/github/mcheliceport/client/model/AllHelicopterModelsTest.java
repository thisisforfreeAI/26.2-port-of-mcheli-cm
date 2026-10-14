package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentTestSupport;
import io.github.mcheliceport.content.aircraft.HeliInfo;
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
 * Exit criterion E6: every stock helicopter's body model parses, every part it draws is in the
 * body or in its own file, and every texture it can use exists.
 */
class AllHelicopterModelsTest {

    /**
     * Parts the stock content names but upstream's own model files never had (upstream draws
     * nothing for them, as the port does). Listed so that any other gap fails.
     */
    private static final List<String> UPSTREAM_GAPS = List.of(
            "ah-1z: part canopy is neither in the body nor in a file",
            "uh-1c: part camera0 is neither in the body nor in a file");

    @Test
    void everyStockHelicopterHasItsModelsAndTextures() {
        ContentRegistries.Snapshot stock = ContentTestSupport.load(ContentTestSupport.stockPack());
        PathPackResources pack = new PathPackResources(new PackLocationInfo("mcheli_test", Component.empty(),
                PackSource.DEFAULT, Optional.empty()), ContentTestSupport.stockPack());
        List<String> problems = new ArrayList<>();
        int parts = 0;
        try (MultiPackResourceManager resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
                List.of(pack))) {
            for (HeliInfo info : stock.heli().values()) {
                AircraftModels.VehicleModels models = AircraftModels.load(resources, "helicopters", info);
                if (models == null) {
                    problems.add(info.name + ": no body model");
                    continue;
                }
                for (String part : AircraftModels.partNames(info)) {
                    parts++;
                    if (models.part(part).isEmpty()) {
                        problems.add(info.name + ": part " + part + " is neither in the body nor in a file");
                    }
                }
                for (String texture : info.getTextureNames()) {
                    checkTexture(resources, info.name, texture, problems);
                }
            }
        }
        assertEquals(UPSTREAM_GAPS, problems);
        assertEquals(34, stock.heli().size(), "stock helicopters");
        assertTrue(parts > 250, "parts checked: " + parts);
    }

    private static void checkTexture(ResourceManager resources, String heli, String texture, List<String> problems) {
        String path = "textures/helicopters/" + texture + ".png";
        if (!Identifier.isValidPath(path)) {
            problems.add(heli + ": texture name " + texture + " is not a valid resource path");
            return;
        }
        if (resources.getResource(Identifier.fromNamespaceAndPath("mcheli", path)).isEmpty()) {
            problems.add(heli + ": texture " + path.toLowerCase(Locale.ROOT) + " is missing");
        }
    }
}
