package io.github.mcheliceport.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.content.parser.yaml.YamlParser;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.content.weapon.hbm.ChemicalContainer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Weapon {@code NTM} blocks parse into the payload settings the NTM Next bridge reads. */
class NtmPayloadParseTest {

    private static final ContentLocation LOCATION = new ContentLocation("test", "sample");

    private static WeaponInfo weapon(String yaml) throws Exception {
        WeaponInfo info = YamlParser.INSTANCE.parseWeapon(LOCATION, "sample.yml", List.of(yaml.split("\n")), false);
        info.validate();
        return info;
    }

    @Test
    void chemicalBlockSetsTypeCountAndSpeed() throws Exception {
        WeaponInfo info = weapon("""
                Type: rocket
                NTM:
                  PayloadType: CHEMICAL
                  Chemical:
                    Type: pink cloud
                    Count: 12
                    Speed: 0.5
                """);
        assertTrue(info.useHBM);
        assertEquals(WeaponInfo.Payload.NTM_CHEMICAL, info.payloadNTM);
        assertEquals(new ChemicalContainer(0.5, 12, ChemicalContainer.ChemType.PINK_CLOUD), info.chemicalContainer);
    }

    @Test
    void chemicalBlockDefaultsToChlorine() throws Exception {
        WeaponInfo info = weapon("""
                Type: rocket
                NTM:
                  PayloadType: CHEMICAL
                  Chemical: {Count: -3}
                """);
        assertEquals(new ChemicalContainer(ChemicalContainer.DEFAULT_SPEED, 0, ChemicalContainer.ChemType.CHLORINE),
                info.chemicalContainer);
    }

    @Test
    @SuppressWarnings("unchecked")
    void vntKeepsItsPartMapsForTheBridge() throws Exception {
        WeaponInfo info = weapon("""
                Type: rocket
                NTM:
                  PayloadType: VNT
                  EffectOnly: true
                  VNT:
                    Preset: standard
                    Effect: large
                    Attributes:
                      BlockAllocator: {Type: BlockAllocatorStandard, Constructor: [24]}
                      BlockProcessor:
                        Type: BlockProcessorStandard
                        NoDrop: true
                        WithBlockEffect: {Type: BlockMutatorFire}
                """);
        assertTrue(info.effectOnly);
        assertNotNull(info.vntSettingContainer);
        assertEquals("standard", info.vntSettingContainer.preset);
        assertEquals(6.5F, info.vntSettingContainer.explosionEffect.cloudScale);
        Map<String, Object> processor = (Map<String, Object>) info.vntSettingContainer.rawEntry.get("BlockProcessor");
        assertEquals(true, processor.get("NoDrop"));
        assertEquals(Map.of("Type", "BlockMutatorFire"), processor.get("WithBlockEffect"));
    }
}
