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
    void stockModelsKeepThePinnedAssetsIncludingBrokenEjectionGroupNames() throws Exception {
        var pack = new PathPackResources(new PackLocationInfo("parachute_test", Component.empty(),
                PackSource.DEFAULT, Optional.empty()), ContentTestSupport.stockPack());
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack))) {
            for (int type = 1; type <= 2; type++) {
                McheliModel model = AircraftModels.loadModel(resources, "parachute" + type, false);
                assertNotNull(model);
                assertTrue(model.vertexCount() > 0);
                if (type == 2) {
                    // Upstream's whitespace parser reads every exported name as "[Exported".
                    // Pin the exact broken asset; do not silently accept a new model gap.
                    assertTrue(model.part("$seat").isEmpty());
                    assertTrue(model.part("$parachute").isEmpty());
                    try (var stream = resources.getResource(Identifier.fromNamespaceAndPath("mcheli",
                            "models/parachute2.mqo")).orElseThrow().open()) {
                        assertEquals("8ef51b1822f436169927a93bd1217fd9e3aa6ecad51d9665685c511b011a5e7b",
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
