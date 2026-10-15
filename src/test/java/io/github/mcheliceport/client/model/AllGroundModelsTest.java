package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import io.github.mcheliceport.content.ContentTestSupport;
import java.util.ArrayList;
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

class AllGroundModelsTest {
    @Test void everyStockGroundVehicleHasItsModelsAndTextures() throws Exception {
        var stock = ContentTestSupport.load(ContentTestSupport.stockPack());
        var pack = new PathPackResources(new PackLocationInfo("ground_models", Component.empty(), PackSource.DEFAULT,
                Optional.empty()), ContentTestSupport.stockPack());
        List<String> missing = new ArrayList<>();
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack))) {
            for (var source : List.of(stock.tank(), stock.vehicle())) {
                for (var info : source.values()) {
                    String dir = info.getDirectoryName();
                    var model = AircraftModels.load(resources, dir, info);
                    if (model == null) { missing.add(dir + "/" + info.name + ": body"); continue; }
                    for (String part : AircraftModels.partNames(info)) {
                        if (model.part(part).isEmpty()) { missing.add(dir + "/" + info.name + ": " + part); }
                    }
                    for (String texture : info.getTextureNames()) {
                        if (resources.getResource(Identifier.fromNamespaceAndPath("mcheli", "textures/" + dir + "/" + texture + ".png")).isEmpty()) {
                            missing.add(dir + "/" + info.name + ": texture " + texture);
                        }
                    }
                }
            }
        }
        assertEquals(12, stock.tank().size()); assertEquals(18, stock.vehicle().size());
        // The pinned upstream MXT-MV definition names a canopy absent from its own model.
        assertEquals(List.of("tanks/mxtmv: canopy"), missing);
    }
}
