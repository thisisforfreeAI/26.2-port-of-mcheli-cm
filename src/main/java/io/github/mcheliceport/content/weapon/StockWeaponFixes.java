package io.github.mcheliceport.content.weapon;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.content.ContentRegistry;
import io.github.mcheliceport.content.parser.yaml.WeaponParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * Optional restoration of the stock weapon settings that upstream's TXT to YAML conversion lost
 * (a port addition): damage factors, bomblets, fuel-air explosives, dispensed items and targeting
 * pod ranges. The values come from {@code mcheli_stock_fixes/weapons.yml}, which holds upstream's
 * legacy TXT values in the weapon YAML format.
 *
 * <p>Each affected weapon keeps two copies of the fields the fixes touch: the stock YAML values
 * and the restored ones. {@link #apply} switches between them, so a server can turn the setting
 * on or off at the next start without reloading content. Only weapons accepted by the
 * {@code isStock} filter are changed: an addon that redefines a stock weapon keeps its own values.
 */
@SuppressWarnings("unchecked")
public final class StockWeaponFixes {

    public static final String RESOURCE = "/mcheli_stock_fixes/weapons.yml";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static @Nullable ContentRegistry<WeaponInfo> preparedFor;
    private static Map<WeaponInfo, Pair> prepared = Map.of();
    private static boolean applied;

    private StockWeaponFixes() {
    }

    /** The fixes, by weapon name, as weapon YAML fragments. */
    public static Map<String, Map<String, Object>> load() {
        try (InputStream in = StockWeaponFixes.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOGGER.warn("Stock weapon fixes {} are missing from the mod jar", RESOURCE);
                return Map.of();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Object root = new Yaml().load(reader);
                return root instanceof Map<?, ?> map ? (Map<String, Map<String, Object>>) map : Map.of();
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Could not read the stock weapon fixes {}", RESOURCE, e);
            return Map.of();
        }
    }

    /**
     * Records the stock and restored values of every fixed weapon of {@code weapons}. Weapons are
     * left with their stock values. Called again for a new registry (after a content reload).
     */
    public static synchronized void prepare(ContentRegistry<WeaponInfo> weapons, Predicate<WeaponInfo> isStock) {
        if (preparedFor == weapons) {
            return;
        }
        Map<String, Map<String, Object>> fixes = load();
        Map<WeaponInfo, Pair> pairs = new IdentityHashMap<>();
        for (Map.Entry<String, Map<String, Object>> fix : fixes.entrySet()) {
            WeaponInfo info = weapons.get(fix.getKey());
            if (info == null || !isStock.test(info)) {
                continue;
            }
            Fields stock = Fields.of(info);
            try {
                WeaponParser.parse(info, new LinkedHashMap<>(fix.getValue()));
                Fields.normalise(info);
                pairs.put(info, new Pair(stock, Fields.of(info)));
            } catch (RuntimeException e) {
                LOGGER.error("Invalid stock weapon fix for {}", fix.getKey(), e);
            } finally {
                stock.writeTo(info);
            }
        }
        preparedFor = weapons;
        prepared = pairs;
        applied = false;
        LOGGER.debug("Prepared {} stock weapon fixes", pairs.size());
    }

    /** Gives every prepared weapon its restored values ({@code true}) or its stock YAML values. */
    public static synchronized void apply(boolean restore) {
        for (Map.Entry<WeaponInfo, Pair> entry : prepared.entrySet()) {
            (restore ? entry.getValue().fixed() : entry.getValue().stock()).writeTo(entry.getKey());
        }
        if (restore != applied && !prepared.isEmpty()) {
            LOGGER.info("{} the stock weapon settings lost in upstream's YAML conversion ({} weapons)",
                    restore ? "Restored" : "Not restoring", prepared.size());
        }
        applied = restore;
    }

    /** Whether the restored values are in place. */
    public static synchronized boolean isApplied() {
        return applied;
    }

    /** The number of weapons the last {@link #prepare} found a fix for. */
    public static synchronized int preparedCount() {
        return prepared.size();
    }

    private record Pair(Fields stock, Fields fixed) {
    }

    /** The weapon fields the fixes set, plus the ones {@link WeaponInfo#validate} derives from them. */
    private record Fields(@Nullable DamageFactor damageFactor, int bomblet, int bombletSTime, float bombletDiff,
            boolean isFAE, int explosionInWater, @Nullable String dispenseItemLoc, int dispenseDamege,
            int dispenseRange, float length, float radius, float angle, int markTime) {

        static Fields of(WeaponInfo w) {
            return new Fields(w.damageFactor, w.bomblet, w.bombletSTime, w.bombletDiff, w.isFAE, w.explosionInWater,
                    w.dispenseItemLoc, w.dispenseDamege, w.dispenseRange, w.length, w.radius, w.angle, w.markTime);
        }

        void writeTo(WeaponInfo w) {
            w.damageFactor = this.damageFactor;
            w.bomblet = this.bomblet;
            w.bombletSTime = this.bombletSTime;
            w.bombletDiff = this.bombletDiff;
            w.isFAE = this.isFAE;
            w.explosionInWater = this.explosionInWater;
            w.dispenseItemLoc = this.dispenseItemLoc;
            w.dispenseDamege = this.dispenseDamege;
            w.dispenseRange = this.dispenseRange;
            w.length = this.length;
            w.radius = this.radius;
            w.angle = this.angle;
            w.markTime = this.markTime;
        }

        /** The rules of {@link WeaponInfo#validate} for these fields. */
        static void normalise(WeaponInfo w) {
            if (w.explosion <= 0) {
                w.isFAE = false;
            }
            if (w.isFAE) {
                w.explosionInWater = 0;
            }
            if (w.bomblet > 0 && w.bombletSTime < 1) {
                w.bombletSTime = 1;
            }
            w.angle = (float) (Math.atan2(w.radius, w.length) * 180.0D / Math.PI);
        }
    }
}
