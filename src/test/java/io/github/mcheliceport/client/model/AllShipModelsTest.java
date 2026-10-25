package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentTestSupport;
import io.github.mcheliceport.content.aircraft.ShipInfo;
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
 * Every stock ship's body model parses, every part it draws is in the
 * body or in its own file, and every texture it can use exists.
 */
class AllShipModelsTest {

    @Test
    void everyStockShipHasItsModelsAndTextures() throws java.io.IOException {
        ContentRegistries.Snapshot stock = ContentTestSupport.load(ContentTestSupport.stockPack());
        PathPackResources pack = new PathPackResources(new PackLocationInfo("mcheli_test", Component.empty(),
                PackSource.DEFAULT, Optional.empty()), ContentTestSupport.stockPack());
        List<String> problems = new ArrayList<>();
        int parts = 0;
        try (MultiPackResourceManager resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
                List.of(pack))) {
            for (ShipInfo info : stock.ship().values()) {
                AircraftModels.VehicleModels models = AircraftModels.load(resources, "ships", info);
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
        assertEquals(List.of(), problems);
        assertEquals(5, stock.ship().size(), "stock ships");
        assertTrue(parts > 10, "parts checked: " + parts);
    }

    private static void checkTexture(ResourceManager resources, String heli, String texture, List<String> problems) {
        String path = "textures/ships/" + texture + ".png";
        if (!Identifier.isValidPath(path)) {
            problems.add(heli + ": texture name " + texture + " is not a valid resource path");
            return;
        }
        if (resources.getResource(Identifier.fromNamespaceAndPath("mcheli", path)).isEmpty()) {
            problems.add(heli + ": texture " + path.toLowerCase(Locale.ROOT) + " is missing");
        }
    }
}
