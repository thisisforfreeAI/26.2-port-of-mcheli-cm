package io.github.mcheliceport.content.hud;

import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Normalises HUD expressions as upstream did before evaluating them.
 *
 * <p>Ported from upstream {@code MCH_HudItem#toFormula} and {@code #preprocessCond}. The
 * expressions themselves are evaluated by the HUD renderer (M4, decision D4).
 */
public final class HudFormula {

    /**
     * Variables that old HUD files compared with 0 or 1 but that the expression engine treats as
     * booleans; comparisons with them are rewritten into plain boolean tests.
     */
    public static final List<String> LEGACY_VARS = List.of("reloading", "is_heat_wpn", "have_radar",
            "dsp_mt_dist", "can_flare", "have_flare", "gunner_mode", "test_mode", "is_uav");

    private HudFormula() {
    }

    /**
     * Lowercases, turns {@code #} into {@code 0x}, removes spaces and tabs and a leading {@code +},
     * then rewrites legacy boolean comparisons. Null becomes {@code "0"}.
     */
    public static String toFormula(@Nullable String s) {
        if (s == null) {
            return "0";
        }
        String f = s.toLowerCase(Locale.ROOT);
        f = f.replace("#", "0x");
        f = f.replace("\t", " ").replace(" ", "");
        if (f.startsWith("+")) {
            f = f.substring(1);
        }
        return preprocessCond(f);
    }

    /**
     * Rewrites {@code var==0} and {@code var!=1} into {@code !var}, and {@code var!=0} and
     * {@code var==1} into {@code var}, for expressions that start with a legacy variable.
     */
    public static String preprocessCond(String s) {
        for (String boolVar : LEGACY_VARS) {
            if (s.startsWith(boolVar)) {
                s = s.replaceAll("\\b" + boolVar + "\\s*==\\s*0\\b", "!" + boolVar);
                s = s.replaceAll("\\b" + boolVar + "\\s*!=\\s*0\\b", boolVar);
                s = s.replaceAll("\\b" + boolVar + "\\s*==\\s*1\\b", boolVar);
                s = s.replaceAll("\\b" + boolVar + "\\s*!=\\s*1\\b", "!" + boolVar);
            }
        }
        return s;
    }
}
