package io.github.mcheliceport.content;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.content.emitter.YamlEmitter;
import io.github.mcheliceport.content.parser.txt.TxtParser;
import io.github.mcheliceport.content.parser.yaml.YamlParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class EngineOutletTest {
    @Test void txtOutletsSurviveYamlConversionWithDirectionSizeAndJetIntent() throws Exception {
        var location = ContentLocation.builtin("engine_test");
        var heli = TxtParser.INSTANCE.parseHelicopter(location, "engine_test.txt", List.of(
                "AddEngineOutlet = 1.2,2.5,-5.1,0,0,-2,1.1,false",
                "AddEngineOutlet = -1.2,2.5,-5.1,0,0,-1,0.8,true"), false);
        String yaml = new YamlEmitter().emitHelicopter(heli);
        var restored = YamlParser.INSTANCE.parseHelicopter(location, "engine_test.yml", List.of(yaml.split("\n")), false);
        assertEquals(heli.engineOutlets, restored.engineOutlets);
        assertEquals(2, restored.engineOutlets.size());
        assertFalse(restored.engineOutlets.getFirst().jet());
        assertTrue(restored.engineOutlets.getLast().jet());
    }
}
