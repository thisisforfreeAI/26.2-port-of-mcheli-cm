package io.github.mcheliceport.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentTestSupport;
import io.github.mcheliceport.content.hud.Hud;
import io.github.mcheliceport.content.hud.HudItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Exit criterion E1 of docs/M4_PLAN.md: the port's HUD expression evaluator gives upstream's
 * result for every expression of every stock HUD. Aviator 5.4.3, upstream's engine, is the oracle
 * (a test-only dependency); its result is read as upstream's {@code MCH_HudItem#calc} did.
 */
class HudExpressionTest {

    private static final AviatorEvaluatorInstance AVIATOR = AviatorEvaluator.newInstance();

    /** Upstream's HUD variables with the Java types {@code MCH_HudItem#updateVarMap} gave them. */
    static final Map<String, Function<Random, Object>> VARIABLES = new LinkedHashMap<>();

    static {
        double[] edges = {0.0, 1.0, -1.0, 0.5, 2.0, 10.0, 0.01, 0.2, 0.1, 0.7, 13000, 90, -90, 180, -180};
        Function<Random, Object> dbl = r -> r.nextInt(3) == 0 ? edges[r.nextInt(edges.length)]
                : (r.nextDouble() - 0.5) * Math.pow(10, r.nextInt(5));
        Function<Random, Object> flt = r -> (float) (double) (Double) dbl.apply(r);
        Function<Random, Object> integer = r -> r.nextInt(4) == 0 ? r.nextInt(5) - 1 : r.nextInt(2000) - 500;
        Function<Random, Object> small = r -> r.nextInt(4);
        Function<Random, Object> bool = Random::nextBoolean;
        VARIABLES.put("color", r -> (double) (r.nextInt() & 0xFFFFFFFFL));
        VARIABLES.put("center_x", r -> 100.0 + r.nextInt(400) / 2.0);
        VARIABLES.put("center_y", r -> 60.0 + r.nextInt(200) / 2.0);
        VARIABLES.put("width", r -> 200.0 + r.nextInt(800));
        VARIABLES.put("height", r -> 120.0 + r.nextInt(400));
        VARIABLES.put("time", r -> (long) r.nextInt(24000));
        VARIABLES.put("test_mode", bool);
        for (String name : List.of("plyr_yaw", "plyr_pitch", "brl_yaw", "brl_pitch", "yaw", "pitch", "roll",
                "radar_rot", "fuel", "reload_time", "mt_dist", "cam_zoom")) {
            VARIABLES.put(name, flt);
        }
        for (String name : List.of("sea_alt", "hp_rto", "throttle", "pos_x", "pos_y", "pos_z", "motion_x",
                "motion_y", "motion_z", "speed", "stick_x", "stick_y", "wpn_heat", "lock", "uav_fs")) {
            VARIABLES.put(name, dbl);
        }
        for (String name : List.of("altitude", "hp", "max_hp", "inventory")) {
            VARIABLES.put(name, integer);
        }
        for (String name : List.of("low_fuel", "sight_type", "has_airburst", "vtol_stat", "free_look", "cam_mode",
                "auto_pilot")) {
            VARIABLES.put(name, small);
        }
        for (String name : List.of("have_radar", "reloading", "is_heat_wpn", "dsp_mt_dist", "gunner_mode",
                "have_flare", "can_flare", "hovering", "is_uav")) {
            VARIABLES.put(name, bool);
        }
    }

    /** Every expression of every stock HUD, YAML and legacy TXT, after upstream's normalisation. */
    static TreeSet<String> stockExpressions() {
        TreeSet<String> out = new TreeSet<>();
        for (ContentRegistries.Snapshot snapshot : List.of(ContentTestSupport.load(ContentTestSupport.stockPack()),
                ContentTestSupport.load(ContentTestSupport.legacyPack()))) {
            for (Hud hud : snapshot.hud().values()) {
                for (HudItem item : hud.list) {
                    out.addAll(expressions(item));
                }
            }
        }
        return out;
    }

    static List<String> expressions(HudItem item) {
        List<String> out = new ArrayList<>();
        switch (item) {
            case HudItem.CameraRot i -> Collections.addAll(out, i.drawPosX, i.drawPosY);
            case HudItem.Color i -> out.add(i.updateColor);
            case HudItem.Conditional i -> {
                if (!i.isEndif) {
                    out.add(i.conditional);
                }
            }
            case HudItem.Graduation i -> Collections.addAll(out, i.drawRot, i.drawRoll, i.drawPosX, i.drawPosY);
            case HudItem.Line i -> Collections.addAll(out, i.pos);
            case HudItem.LineStipple i -> {
                Collections.addAll(out, i.pos);
                Collections.addAll(out, i.pat, i.fac);
            }
            case HudItem.Radar i -> Collections.addAll(out, i.rot, i.left, i.top, i.width, i.height);
            case HudItem.Rect i -> Collections.addAll(out, i.left, i.top, i.width, i.height);
            case HudItem.Str i -> Collections.addAll(out, i.posX, i.posY);
            case HudItem.Texture i -> Collections.addAll(out, i.left, i.top, i.width, i.height, i.uLeft, i.vTop,
                    i.uWidth, i.vHeight, i.rot);
            case HudItem.Call i -> {
            }
            case HudItem.Exit i -> {
            }
        }
        return out;
    }

    /** Upstream's {@code MCH_HudItem#calc}; "error" when Aviator throws or returns another type. */
    private static String aviator(String expr, Map<String, Object> vars) {
        try {
            Object result = AVIATOR.compile(expr, true).execute(new HashMap<>(vars));
            if (result instanceof Number n) {
                return Double.toString(n.doubleValue());
            }
            if (result instanceof Boolean b) {
                return b ? "1.0" : "0.0";
            }
            return "error";
        } catch (RuntimeException e) {
            return "error";
        }
    }

    private static String port(String expr, Map<String, Object> vars) {
        try {
            return Double.toString(HudExpression.calc(expr, HudExpression.Variables.of(vars)));
        } catch (HudExpression.HudExpressionException e) {
            return "error";
        }
    }

    @Test
    void everyStockExpressionMatchesAviator() {
        TreeSet<String> exprs = stockExpressions();
        assertTrue(exprs.size() > 200, "expected the stock HUDs' expressions, found " + exprs.size());
        List<String> failures = new ArrayList<>();
        Random random = new Random(0x4D344855L);
        int errors = 0;
        for (int round = 0; round < 40; round++) {
            Map<String, Object> vars = new HashMap<>();
            VARIABLES.forEach((name, gen) -> vars.put(name, gen.apply(random)));
            for (String expr : exprs) {
                String expected = aviator(expr, vars);
                String actual = port(expr, vars);
                if (expected.equals("error")) {
                    errors++;
                }
                if (!expected.equals(actual) && failures.size() < 30) {
                    failures.add(expr + " with " + vars + ": Aviator " + expected + ", port " + actual);
                }
            }
        }
        if (!failures.isEmpty()) {
            fail(failures.size() + " differences:\n" + String.join("\n", failures));
        }
        // No stock expression should fail in Aviator with upstream's variable types.
        assertEquals(0, errors, "stock expressions that Aviator could not evaluate");
    }

    @Test
    void handWrittenCasesMatchAviator() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("b", true);
        vars.put("f", false);
        vars.put("i", 7);
        vars.put("d", 2.5);
        vars.put("fl", 2.5F);
        vars.put("l", 5L);
        vars.put("n", -3);
        String[] cases = {"b==1", "b==0", "f==0", "b!=1", "1==b", "b==true", "b>0", "i/2", "-i/2", "i%3", "-i%3",
                "d%2", "-7.5%2", "0xcfffffff", "0xffffffffff", "0xcfffffff|1", "1<<24", "1<<63", "1<<64", "-1>>1",
                "-1>>>1", "~5", "5&3", "5^3", "i/0", "i%0", "d/0", "i/0.0", "b?1:2", "i?1:2", "!i", "i&&b", "b&&1",
                "1+b", "undef", "undef+1", "undef==0", "undef<1", "undef>1", "2**3", "2**0.5", "-2**2", "2**-1",
                "3.0", "1e3", "1.5e2", ".5", "5.", "1/3", "1/3.0", "math.abs(-3)", "math.abs(-3.5)", "math.abs(n)",
                "math.sqrt(4)", "math.pow(2,3)", "math.round(2.5)", "math.round(-2.5)", "math.floor(2.5)",
                "math.ceil(2.1)", "math.sin(0)", "math.cos(d)", "math.tan(1)", "math.asin(0.5)", "math.acos(0.5)",
                "math.log(1)", "math.log10(100)", "math.atan(1)", "long(2.7)", "long(-2.7)", "double(2)",
                "max(1,2.5)", "max(3,2.5)", "min(1,2)", "min(i,d,l)", "1<2==true", "1+2*3-4/2", "(1+2)*3",
                "i>3&&d<3||f", "f&&i>3||b", "1<2?3<4?5:6:7", "b?f?1:2:3", "-d", "-(-i)", "!!b", "!f", "!b==f",
                "i==7.0", "d==2.5", "fl==2.5", "i+fl", "i*l", "i-l*2", "9223372036854775807+1", "1==1.0", "nil",
                "nil==0", "nil==nil", "b&1", "b+1", "1-2-3", "2*3%4", "8/2/2", "2<<1+1", "1|2&3", "1|2^3", "true",
                "false?1:0", "0x1F", "0XFF", "07", "08", "0.1+0.2", "100000000000*100000000000", "f<b", "b>=f",
                "((0xcf)<<24)|((0xff)<<16)|((0x80)<<8 )|((i)<<0 )", "-i<<2", "i>>1", "-i>>1", "i>>>1",
                "d<<1", "d*-1", "i*-1", "i- -1", "3-+2", "+3"};
        List<String> failures = new ArrayList<>();
        for (String expr : cases) {
            String expected = aviator(expr, vars);
            String actual = port(expr, vars);
            if (!expected.equals(actual)) {
                failures.add(expr + ": Aviator " + expected + ", port " + actual);
            }
        }
        if (!failures.isEmpty()) {
            fail(String.join("\n", failures));
        }
    }

    @Test
    void syntaxErrorsAreReported() {
        for (String bad : List.of("", "1+", "(1", "1)", "foo(1)", "1 2", "0x", "a.b(", "1M", "@")) {
            Map<String, Object> vars = Map.of();
            assertEquals("error", port(bad, vars), bad);
        }
    }
}
