package io.github.mcheliceport.content;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.content.emitter.YamlEmitter;
import io.github.mcheliceport.content.parser.txt.TxtParser;
import io.github.mcheliceport.content.parser.yaml.YamlParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class MachOneSpeedTest {
    @Test void onlyExplicitlyCalibratedStockAircraftHaveACosmeticMachMapping(){
        var stock=ContentTestSupport.load(ContentTestSupport.stockPack());
        for(String name:List.of("f-35a","f-35b","f-35c","f-15e","f-15s_mtd","f14d","f22a","f4a","fa18f","mig29","su-33","su-47","su37","tornado-gr4","tornado-ids"))assertTrue(stock.plane().get(name).machOneSpeed>0,name);
        for(String name:List.of("a-10","b-2a"))assertEquals(0,stock.plane().get(name).machOneSpeed,name);
    }
    @Test void addonMachMappingSurvivesTxtYamlConversion()throws Exception{
        var location=ContentLocation.builtin("mach_test");
        var txt=TxtParser.INSTANCE.parsePlane(location,"mach_test.txt",List.of("MachOneSpeed = 0.75"),false);
        var yaml=new YamlEmitter().emitPlane(txt);
        var restored=YamlParser.INSTANCE.parsePlane(location,"mach_test.yml",List.of(yaml.split("\n")),false);
        assertEquals(.75F,restored.machOneSpeed);
    }
}
