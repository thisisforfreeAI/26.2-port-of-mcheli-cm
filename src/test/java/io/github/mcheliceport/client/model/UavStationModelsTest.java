package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.content.ContentTestSupport;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.Test;

class UavStationModelsTest {
    @Test void stockConsolesHaveUsableModelsTexturesAndCoverGroups() throws Exception {
        var pack = new PathPackResources(new PackLocationInfo("uav_test", Component.empty(), PackSource.DEFAULT, Optional.empty()), ContentTestSupport.stockPack());
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack))) {
            for (String name : List.of("uav_station", "uav_portable_controller")) {
                var model = AircraftModels.loadModel(resources, name, false);
                assertNotNull(model); assertTrue(model.vertexCount() > 0);
                for (String suffix : List.of("", "_on")) {
                    assertTrue(resources.getResource(Identifier.fromNamespaceAndPath("mcheli", "textures/" + name + suffix + ".png")).isPresent());
                }
                if (name.equals("uav_portable_controller")) {
                    for (String part : List.of("$body", "$cover", "$laptop_cover", "$display")) {
                        assertFalse(model.part(part).isEmpty(), "missing portable part " + part);
                    }
                }
            }
        }
    }
}
