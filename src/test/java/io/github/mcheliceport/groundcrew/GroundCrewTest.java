package io.github.mcheliceport.groundcrew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.mcheliceport.client.hud.GroundCrewHudLayer;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentTestSupport;
import io.github.mcheliceport.content.aircraft.CrewRole;
import io.github.mcheliceport.content.aircraft.VehicleInfo;
import io.github.mcheliceport.content.emitter.YamlEmitter;
import io.github.mcheliceport.content.parser.txt.TxtParser;
import io.github.mcheliceport.content.parser.yaml.YamlParser;
import java.util.List;
import org.junit.jupiter.api.Test;

/** M10c ground crew on plain values and the stock ammo cart. */
class GroundCrewTest {

    @Test
    void stockAmmoCartCarriesAStockAndALoader() {
        ContentRegistries.Snapshot stock = ContentTestSupport.load(ContentTestSupport.stockPack());
        VehicleInfo cart = stock.vehicle().get("ammo_cart");
        assertEquals(600, cart.ammoSupplyStock);
        assertEquals(8.0F, cart.ammoSupplyRange);
        assertEquals(CrewRole.LOADER, cart.seatList.getFirst().role);
        assertFalse(cart.recipeString.isEmpty(), "the cart has a survival recipe");
        assertEquals(0, stock.vehicle().get("ammo_box").ammoSupplyStock, "the ammo box keeps its endless supply");
    }

    @Test
    void stockRoundTripsThroughYaml() throws Exception {
        ContentLocation location = ContentLocation.builtin("stock_test");
        VehicleInfo info = TxtParser.INSTANCE.parseVehicle(location, "stock_test.txt", List.of(
                "AddSeat = 0, 1, 0",
                "AmmoSupplyRange = 8",
                "AmmoSupplyStock = 250"), false);
        assertEquals(250, info.ammoSupplyStock);
        String yaml = new YamlEmitter().emitVehicle(info);
        VehicleInfo restored = YamlParser.INSTANCE.parseVehicle(location, "stock_test.yml", List.of(yaml.split("\n")),
                false);
        assertEquals(250, restored.ammoSupplyStock);
    }

    @Test
    void hudWordsKeepTheirOrder() {
        assertEquals(List.of(), GroundCrewHudLayer.words(0));
        assertEquals(List.of("REPAIR", "REARM", "REFUEL", "RESTOCK"), GroundCrewHudLayer.words(
                GroundCrew.RESTOCK | GroundCrew.REFUEL | GroundCrew.REARM | GroundCrew.REPAIR));
        assertEquals(List.of("REFUEL"), GroundCrewHudLayer.words(GroundCrew.REFUEL));
    }
}
