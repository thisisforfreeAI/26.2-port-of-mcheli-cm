package io.github.mcheliceport.content;

import static org.junit.jupiter.api.Assertions.*;

import io.github.mcheliceport.content.emitter.YamlEmitter;
import io.github.mcheliceport.content.parser.yaml.YamlParser;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.content.weapon.hbm.ChemicalContainer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Addon NTM settings must survive YAML export, even without HBM installed. */
class NtmYamlTest {
    private static final ContentLocation LOCATION = new ContentLocation("test", "ntm_roundtrip");

    private static WeaponInfo parse(String yaml) throws Exception {
        return YamlParser.INSTANCE.parseWeapon(LOCATION, "ntm_roundtrip.yml", yaml.lines().toList(), false);
    }

    @Test
    void everyPayloadAndAllSettingsSurviveExportAndReload() throws Exception {
        for (var payload : WeaponInfo.Payload.values()) {
            WeaponInfo before = parse("""
                    Type: rocket
                    NTM:
                      PayloadType: %s
                      EffectOnly: true
                      MiniNuke:
                        UseTorex: true
                        Safe: true
                        BlastRadius: 12.5
                        KillRadius: 23.5
                        RadiationLevel: 0.25
                        Particle: TinyTot
                        ShrapnelCount: 3
                        Resolution: 24
                        Attributes: [FIRE, NODROP]
                      Chemical: {Type: PINK_CLOUD, Count: 4, Speed: 0.125}
                      Fire:
                        Type: PHOSPHORUS
                        Count: 3
                        Width: 7.5
                        Height: 4.5
                        AreaSpread: 2
                        Lifetime: 140
                        LifetimeVariance: 12
                        Particles: false
                      Mist:
                        Fluid: CHLORINE
                        Count: 5
                        Width: 11.5
                        Height: 6.5
                        AreaSpread: 4
                        Lifetime: 100
                        LifetimeVariance: 10
                      NT: {Resolution: 32, Attributes: [NOHURT, NOSOUND]}
                      VNT:
                        Preset: AMAT
                        Attributes:
                          BlockAllocator: {Type: BlockAllocatorStandard, Constructor: [8]}
                          BlockProcessor:
                            Type: BlockProcessorStandard
                            NoDrop: true
                            WithBlockEffect: {Type: BlockMutatorFire}
                          SFX: [{Type: ExplosionEffectStandard}]
                        Effect:
                          IsSmall: true
                          CloudCount: 5
                          CloudScale: 1.25
                          CloudSpeedMult: 0.75
                          WaveScale: 17
                          DebrisCount: 4
                          DebrisSize: 6
                          DebrisRetry: 8
                          DebrisVelocity: 0.5
                          DebrisHorizontalDeviation: 2
                          DebrisVerticalOffset: -1
                          SoundRange: 99
                    """.formatted(payload.name()));
            assertEquals(payload, before.payloadNTM, "canonical payload name");
            String exported = new YamlEmitter().emitWeapon(before);
            WeaponInfo after = parse(exported);
            assertTrue(after.useHBM);
            assertEquals(payload, after.payloadNTM);
            assertTrue(after.effectOnly);
            DeepCompare compare = new DeepCompare(Set.of(), 1e-6);
            compare.compare("MiniNuke", before.mukeContainer, after.mukeContainer);
            compare.compare("Fire", before.fireContainer, after.fireContainer);
            compare.compare("Mist", before.mistContainer, after.mistContainer);
            compare.compare("Chemical", before.chemicalContainer, after.chemicalContainer);
            compare.compare("NT", before.ntSettingContainer, after.ntSettingContainer);
            compare.compare("VNT", before.vntSettingContainer, after.vntSettingContainer);
            assertEquals(List.of(), compare.differences(), payload.name());
        }
    }

    @Test
    void miniNukePresetsKeepTheirValuesAndLegacyAttributes() throws Exception {
        for (String preset : List.of("SAFE", "TOTS", "LOW", "MEDIUM", "HIGH")) {
            WeaponInfo before = parse("NTM:\n  Type: MININUKE\n  MiniNuke: " + preset);
            WeaponInfo after = parse(new YamlEmitter().emitWeapon(before));
            assertEquals(WeaponInfo.Payload.NTM_MINI_NUKE, after.payloadNTM);
            DeepCompare compare = new DeepCompare(Set.of("attributes"), 1e-6);
            compare.compare("MiniNuke", before.mukeContainer, after.mukeContainer);
            assertEquals(List.of(), compare.differences(), preset);
            assertEquals(before.mukeContainer.attributes, after.mukeContainer.attributes);
        }
    }

    @Test
    void chemicalTypesDefaultsAndZeroCountAreParsed() throws Exception {
        for (var type : ChemicalContainer.ChemType.values()) {
            WeaponInfo info = parse("NTM:\n  Type: CHEMICAL\n  Chemical: {Type: " + type.name().toLowerCase()
                    + ", Count: 0, Speed: 0}");
            assertEquals(new ChemicalContainer(0, 0, type), info.chemicalContainer);
        }
        assertEquals(new ChemicalContainer(0.2, 1, ChemicalContainer.ChemType.CHLORINE),
                parse("NTM:\n  Type: CHEMICAL\n  Chemical: {}").chemicalContainer);
    }

    @Test
    void invalidChemicalSettingsAreRefusedInsteadOfChangingTypeOrTruncatingCount() {
        for (String settings : List.of("Type: unknown", "Count: -1", "Count: 1.5", "Count: 2147483648",
                "Count: .nan", "Speed: -0.1", "Speed: .inf", "Speed: .nan")) {
            assertThrows(IllegalArgumentException.class,
                    () -> parse("NTM:\n  Type: CHEMICAL\n  Chemical: {" + settings + "}"), settings);
        }
    }

    @Test
    void ordinaryWeaponsDoNotAcquireAnNtmBlock() throws Exception {
        Map<?, ?> output = new Yaml().load(new YamlEmitter().emitWeapon(parse("Type: rocket")));
        assertFalse(output.containsKey("NTM"));
    }
}
