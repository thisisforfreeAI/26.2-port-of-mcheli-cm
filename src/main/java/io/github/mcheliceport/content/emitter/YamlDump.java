package io.github.mcheliceport.content.emitter;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.content.BaseInfo;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentRegistry;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.content.aircraft.ShipInfo;
import io.github.mcheliceport.content.aircraft.TankInfo;
import io.github.mcheliceport.content.aircraft.VehicleInfo;
import io.github.mcheliceport.content.hud.Hud;
import io.github.mcheliceport.content.parser.yaml.YamlParser;
import io.github.mcheliceport.content.throwable.ThrowableInfo;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;

/**
 * Writes the loaded content that did not come from YAML files as YAML, one folder per content type
 * (upstream {@code /mcheli dumpyaml}, {@code MCH_Command#handleDumpYaml}). This converts TXT addon
 * packs to YAML.
 */
public final class YamlDump {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern UNSAFE_FILE_NAME_CHARS = Pattern.compile("[^a-zA-Z0-9._-]");

    private YamlDump() {
    }

    /** How many files were written, and how many definitions could not be converted. */
    public record Result(int written, int failed) {
    }

    @FunctionalInterface
    private interface Emit<T> {

        String apply(YamlEmitter emitter, T info) throws YamlEmitter.EmissionException;
    }

    public static Result dump(ContentRegistries.Snapshot content, Path base) throws IOException {
        YamlEmitter emitter = new YamlEmitter();
        Files.createDirectories(base);
        int[] counts = new int[2];
        dump(emitter, base, content.heli(), (e, i) -> e.emitHelicopter((HeliInfo) i), counts);
        dump(emitter, base, content.plane(), (e, i) -> e.emitPlane((PlaneInfo) i), counts);
        dump(emitter, base, content.ship(), (e, i) -> e.emitShip((ShipInfo) i), counts);
        dump(emitter, base, content.tank(), (e, i) -> e.emitTank((TankInfo) i), counts);
        dump(emitter, base, content.vehicle(), (e, i) -> e.emitVehicle((VehicleInfo) i), counts);
        dump(emitter, base, content.weapon(), (e, i) -> e.emitWeapon((WeaponInfo) i), counts);
        dump(emitter, base, content.throwable(), (e, i) -> e.emitThrowable((ThrowableInfo) i), counts);
        dump(emitter, base, content.hud(), (e, i) -> e.emitHud((Hud) i), counts);
        return new Result(counts[0], counts[1]);
    }

    private static void dump(YamlEmitter emitter, Path base, ContentRegistry<? extends BaseInfo> registry,
            Emit<BaseInfo> emit, int[] counts) throws IOException {
        Path dir = base.resolve(registry.getDirectoryName());
        Files.createDirectories(dir);
        for (Map.Entry<String, ? extends BaseInfo> entry : registry.entries()) {
            BaseInfo info = entry.getValue();
            // Like upstream, only content that is not YAML already.
            if (YamlParser.INSTANCE.getIdentifier().equals(info.parserIdentifier)) {
                continue;
            }
            Path out = dir.resolve(UNSAFE_FILE_NAME_CHARS.matcher(entry.getKey()).replaceAll("_") + ".yml");
            try {
                YamlEmitter.writeTo(out, emit.apply(emitter, info));
                counts[0]++;
            } catch (YamlEmitter.EmissionException | RuntimeException e) {
                LOGGER.error("Failed to write {} as YAML", info.getContentPath(), e);
                counts[1]++;
            }
        }
    }
}
