package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.content.ContentTestSupport;
import java.util.List;
import java.util.Optional;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.Test;

class ParachuteModelsTest {
    @Test
    void stockModelsHaveTheEjectionSeatAndCanopyGroups() throws Exception {
        var pack = new PathPackResources(new PackLocationInfo("parachute_test", Component.empty(),
                PackSource.DEFAULT, Optional.empty()), ContentTestSupport.stockPack());
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack))) {
            for (int type = 1; type <= 2; type++) {
                McheliModel model = AircraftModels.loadModel(resources, "parachute" + type, false);
                assertNotNull(model);
                assertTrue(model.vertexCount() > 0);
                if (type == 2) {
                    // Upstream's file prefixed every name with "[Exported] [Imported]", which its
                    // whitespace parser cut to "[Exported", and left $parachute empty. The port's
                    // copy names the seat $seat and starts the canopy and lines with $parachute,
                    // so they merge into that group as upstream's loader merged unnamed objects.
                    assertEquals(1, model.part("$seat").size());
                    assertEquals(1, model.part("$parachute").size());
                    assertEquals(2, model.groups.size());
                    assertTrue(model.part("$seat").getFirst().vertexCount() > 0);
                    try (var stream = resources.getResource(Identifier.fromNamespaceAndPath("mcheli",
                            "models/parachute2.mqo")).orElseThrow().open()) {
                        assertEquals("91170dc9fe5f44ee4a1d61df6cca93c13a819bde571bba60f5cacab68953a6a3",
                                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())));
                    }
                }
                assertTrue(resources.getResource(Identifier.fromNamespaceAndPath("mcheli",
                        "textures/parachute" + type + ".png")).isPresent());
            }
        }
    }

    @Test
    void validAddonObjCanSupplySeparateSeatAndCanopyGroups() throws Exception {
        String obj = """
                v 0.0 0.0 0.0
                v 1.0 0.0 0.0
                v 0.0 1.0 0.0
                g $seat
                f 1 2 3
                g $parachute
                f 3 2 1
                """;
        McheliModel model = ObjParser.parse("parachute2.obj",
                new ByteArrayInputStream(obj.getBytes(StandardCharsets.UTF_8)));
        assertEquals(3, model.part("$seat").getFirst().vertexCount());
        assertEquals(3, model.part("$parachute").getFirst().vertexCount());
    }
}
