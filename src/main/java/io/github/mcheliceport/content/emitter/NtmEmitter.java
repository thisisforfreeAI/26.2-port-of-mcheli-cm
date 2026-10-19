package io.github.mcheliceport.content.emitter;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import java.util.LinkedHashMap;
import java.util.Map;

/** Emits the parsed NTM data, including legacy settings unsupported by the current runtime. */
final class NtmEmitter {
    private NtmEmitter() {}

    static Map<String, Object> emit(WeaponInfo info) {
        Map<String, Object> ntm = new LinkedHashMap<>();
        ntm.put("PayloadType", info.payloadNTM.name());
        ntm.put("EffectOnly", info.effectOnly);
        if (info.mukeContainer != null) {
            var m = info.mukeContainer;
            ntm.put("MiniNuke", Map.of("UseTorex", !m.miniNuke, "Safe", m.safe,
                    "BlastRadius", m.blastRadius, "KillRadius", m.killRadius,
                    "RadiationLevel", m.radiationLevel, "Particle", m.particle,
                    "ShrapnelCount", m.shrapnelCount, "Resolution", m.resolution, "Attributes", m.attributes));
        }
        if (info.fireContainer != null) {
            var f = info.fireContainer;
            ntm.put("Fire", Map.of("Type", f.fireType, "Count", f.count, "Width", f.width, "Height", f.height,
                    "AreaSpread", f.areaSpread, "Lifetime", f.lifetime, "LifetimeVariance", f.lifetimeVariance,
                    "Particles", f.particles));
        }
        if (info.mistContainer != null) {
            var m = info.mistContainer;
            ntm.put("Mist", Map.of("Fluid", m.fluidType, "Count", m.cloudCount, "Width", m.width,
                    "Height", m.height, "AreaSpread", m.areaSpread, "Lifetime", m.lifetime,
                    "LifetimeVariance", m.lifetimeVariance));
        }
        if (info.chemicalContainer != null) {
            var c = info.chemicalContainer;
            ntm.put("Chemical", Map.of("Type", c.type().name(), "Count", c.count(), "Speed", c.speed()));
        }
        if (info.ntSettingContainer != null) {
            var n = info.ntSettingContainer;
            ntm.put("NT", Map.of("Attributes", n.attributes, "Resolution", n.resolution));
        }
        if (info.vntSettingContainer != null) {
            var v = info.vntSettingContainer;
            Map<String, Object> vnt = new LinkedHashMap<>();
            vnt.put("Attributes", v.rawEntry);
            if (v.preset != null) vnt.put("Preset", v.preset);
            var e = v.explosionEffect;
            Map<String, Object> effect = new LinkedHashMap<>();
            effect.put("IsSmall", e.isSmall);
            effect.put("CloudCount", e.cloudCount);
            effect.put("CloudScale", e.cloudScale);
            effect.put("CloudSpeedMult", e.cloudSpeedMult);
            effect.put("WaveScale", e.waveScale);
            effect.put("DebrisCount", e.debrisCount);
            effect.put("DebrisSize", e.debrisSize);
            effect.put("DebrisRetry", e.debrisRetry);
            effect.put("DebrisVelocity", e.debrisVelocity);
            effect.put("DebrisHorizontalDeviation", e.debrisHorizontalDeviation);
            effect.put("DebrisVerticalOffset", e.debrisVerticalOffset);
            effect.put("SoundRange", e.soundRange);
            vnt.put("Effect", effect);
            ntm.put("VNT", vnt);
        }
        return ntm;
    }
}
