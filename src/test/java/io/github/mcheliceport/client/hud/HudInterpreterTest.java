package io.github.mcheliceport.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentRegistry;
import io.github.mcheliceport.content.ContentTestSupport;
import io.github.mcheliceport.content.hud.Hud;
import io.github.mcheliceport.content.hud.HudItem;
import io.github.mcheliceport.content.hud.HudStringArg;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Exit criterion E3 of docs/M4_PLAN.md: the HUD interpreter draws what upstream draws. The oracle
 * ({@link Upstream}) is upstream's {@code MCH_Hud#draw}/{@code drawItems} and the
 * {@code execute} methods of the {@code MCH_HudItem*} classes, transcribed with the GL calls
 * replaced by the same recording canvas and with Aviator 5.4.3 evaluating the expressions, as
 * upstream did. Every stock HUD (YAML and legacy TXT), including {@code heli.yml} and
 * {@code heli_gnr.yml}, is drawn by both for many random frames, with auto-scale on and off.
 */
class HudInterpreterTest {

    /** Records canvas calls as text, with exact doubles. */
    static final class Recorder implements HudCanvas {
        final List<String> calls = new ArrayList<>();

        @Override
        public void line(double[] xy, int color, int mode) {
            this.calls.add("line " + mode + " " + Integer.toHexString(color) + " " + Arrays.toString(xy));
        }

        @Override
        public void lineStipple(double[] xy, int color, int factor, int pattern) {
            this.calls.add("stipple " + factor + " " + pattern + " " + Integer.toHexString(color) + " "
                    + Arrays.toString(xy));
        }

        @Override
        public void rect(double x0, double y0, double x1, double y1, int color) {
            this.calls.add("rect " + x0 + " " + y0 + " " + x1 + " " + y1 + " " + Integer.toHexString(color));
        }

        @Override
        public void points(double[] xy, int color) {
            this.calls.add("points " + Integer.toHexString(color) + " " + Arrays.toString(xy));
        }

        @Override
        public void texture(String name, double left, double top, double width, double height, double uLeft,
                double vTop, double uWidth, double vHeight, float rot) {
            this.calls.add("texture " + name + " " + left + " " + top + " " + width + " " + height + " " + uLeft + " "
                    + vTop + " " + uWidth + " " + vHeight + " " + rot);
        }

        @Override
        public void text(String text, int x, int y, int color, boolean centered) {
            this.calls.add("text " + centered + " " + x + " " + y + " " + Integer.toHexString(color) + " " + text);
        }

        @Override
        public void pushRotation(double x, double y, float degrees) {
            this.calls.add("push " + x + " " + y + " " + degrees);
        }

        @Override
        public void popRotation() {
            this.calls.add("pop");
        }
    }

    private static final Map<HudStringArg, Function<Random, Object>> STRING_ARGS = new HashMap<>();

    static {
        Function<Random, Object> dbl = r -> (r.nextDouble() - 0.5) * Math.pow(10, r.nextInt(5));
        Function<Random, Object> flt = r -> (float) (double) (Double) dbl.apply(r);
        Function<Random, Object> integer = r -> r.nextInt(2000) - 500;
        Function<Random, Object> str = r -> "s" + r.nextInt(100);
        for (HudStringArg arg : HudStringArg.values()) {
            STRING_ARGS.put(arg, str);
        }
        for (HudStringArg arg : List.of(HudStringArg.ALTITUDE, HudStringArg.MC_THOR, HudStringArg.MC_TMIN,
                HudStringArg.MC_TSEC, HudStringArg.MAX_HP, HudStringArg.HP, HudStringArg.INVENTORY)) {
            STRING_ARGS.put(arg, integer);
        }
        for (HudStringArg arg : List.of(HudStringArg.HP_PER, HudStringArg.POS_X, HudStringArg.POS_Y,
                HudStringArg.POS_Z, HudStringArg.MOTION_X, HudStringArg.MOTION_Y, HudStringArg.MOTION_Z,
                HudStringArg.YAW, HudStringArg.PLYR_YAW, HudStringArg.TVM_POS_X, HudStringArg.TVM_POS_Y,
                HudStringArg.TVM_POS_Z, HudStringArg.TVM_DIFF, HudStringArg.UAV_DIST, HudStringArg.THROTTLE)) {
            STRING_ARGS.put(arg, dbl);
        }
        for (HudStringArg arg : List.of(HudStringArg.RELOAD_PER, HudStringArg.RELOAD_SEC, HudStringArg.MORTAR_DIST,
                HudStringArg.PITCH, HudStringArg.ROLL, HudStringArg.PLYR_PITCH, HudStringArg.CAM_ZOOM)) {
            STRING_ARGS.put(arg, flt);
        }
        STRING_ARGS.put(HudStringArg.DATE, r -> new Date(1_700_000_000_000L + r.nextInt(1_000_000)));
        STRING_ARGS.put(HudStringArg.NONE, r -> null);
    }

    private static HudFrame randomFrame(Random r) {
        HudFrame f = new HudFrame();
        int[][] sizes = {{427, 240}, {480, 270}, {640, 360}, {320, 240}, {683, 384}};
        int[] size = sizes[r.nextInt(sizes.length)];
        f.width = size[0];
        f.height = size[1];
        f.autoScale = r.nextBoolean();
        f.testMode = r.nextInt(4) == 0;
        HudExpressionTest.VARIABLES.forEach((name, gen) -> {
            if (!List.of("color", "center_x", "center_y", "width", "height").contains(name)) {
                f.vars.put(name, gen.apply(r));
            }
        });
        STRING_ARGS.forEach((arg, gen) -> f.stringArgs.put(arg, gen.apply(r)));
        f.hasWeapon = r.nextBoolean();
        f.weaponMagSize = r.nextInt(3) - 1;
        f.entityRadar = randomRadar(r);
        f.enemyRadar = randomRadar(r);
        f.aircraftYaw = (r.nextFloat() - 0.5F) * 720.0F;
        f.cameraYaw = (r.nextFloat() - 0.5F) * 720.0F;
        f.cameraPitch = (r.nextFloat() - 0.5F) * 180.0F;
        return f;
    }

    private static double[] randomRadar(Random r) {
        double[] xy = new double[r.nextInt(6) * 2];
        for (int i = 0; i < xy.length; i++) {
            xy[i] = (r.nextDouble() - 0.5) * 128.0;
        }
        return xy;
    }

    private static List<String> port(HudInterpreter interpreter, Hud hud, HudFrame frame) {
        Recorder rec = new Recorder();
        interpreter.draw(hud, frame, rec);
        return rec.calls;
    }

    @Test
    void everyStockHudDrawsAsUpstream() {
        int compared = 0;
        int nonEmpty = 0;
        for (ContentRegistries.Snapshot snapshot : List.of(ContentTestSupport.load(ContentTestSupport.stockPack()),
                ContentTestSupport.load(ContentTestSupport.legacyPack()))) {
            ContentRegistry<Hud> huds = snapshot.hud();
            HudInterpreter interpreter = new HudInterpreter(huds::get);
            Upstream upstream = new Upstream(huds::get);
            Random random = new Random(0x48554449L);
            List<String> failures = new ArrayList<>();
            for (int round = 0; round < 60; round++) {
                HudFrame frame = randomFrame(random);
                for (Hud hud : huds.values()) {
                    List<String> expected = upstream.draw(hud, frame);
                    List<String> actual = port(interpreter, hud, frame);
                    compared++;
                    if (!expected.isEmpty()) {
                        nonEmpty++;
                    }
                    if (!expected.equals(actual) && failures.size() < 5) {
                        failures.add(hud.name + " round " + round + ":\n  upstream " + expected + "\n  port     "
                                + actual);
                    }
                    assertEquals(upstream.colorSetting, interpreter.color(), hud.name + ": colour carried over");
                }
            }
            if (!failures.isEmpty()) {
                fail(String.join("\n", failures));
            }
            assertEquals(0, interpreter.errorCount(), "stock HUD items that failed");
        }
        assertTrue(compared > 1000 && nonEmpty > compared / 2, compared + " frames, " + nonEmpty + " drew something");
    }

    @Test
    void heliHudDrawsItsLayout() {
        ContentRegistries.Snapshot snapshot = ContentTestSupport.load(ContentTestSupport.stockPack());
        Hud heli = snapshot.hud().get("heli");
        Hud gunner = snapshot.hud().get("heli_gnr");
        assertTrue(heli != null && gunner != null, "stock heli HUDs");
        Random random = new Random(7);
        HudFrame frame = randomFrame(random);
        frame.autoScale = false;
        frame.testMode = false;
        frame.vars.put("have_radar", true);
        List<String> calls = port(new HudInterpreter(snapshot.hud()::get), heli, frame);
        assertTrue(calls.stream().anyMatch(c -> c.startsWith("texture heli_hud")), "radar texture: " + calls);
        assertTrue(calls.stream().anyMatch(c -> c.startsWith("push")), "graduations: " + calls);
        List<String> gnr = port(new HudInterpreter(snapshot.hud()::get), gunner, frame);
        assertTrue(gnr.stream().anyMatch(c -> c.startsWith("text false")), "position strings: " + gnr);
    }

    @Test
    void errorsSkipTheItemAndFailedConditionsAreFalse() {
        ContentRegistries.Snapshot snapshot = ContentTestSupport.load(ContentTestSupport.stockPack());
        Hud hud = new Hud(snapshot.hud().get("heli").location, "test.yml", "test");
        hud.list.add(new HudItem.Rect(0, "1/0", "0", "1", "1"));
        hud.list.add(new HudItem.Conditional(0, false, "1+"));
        hud.list.add(new HudItem.Rect(0, "0", "0", "1", "1"));
        hud.list.add(new HudItem.Conditional(0, true, null));
        hud.list.add(new HudItem.Rect(0, "2", "2", "1", "1"));
        HudInterpreter interpreter = new HudInterpreter(name -> null);
        HudFrame frame = new HudFrame();
        frame.width = 100;
        frame.height = 100;
        List<String> calls = port(interpreter, hud, frame);
        assertEquals(List.of("rect 53.0 53.0 52.0 52.0 ff000000"), calls);
        assertEquals(2, interpreter.errorCount());
    }

    /**
     * Upstream's HUD code: {@code MCH_Hud#draw}, {@code drawItems}, {@code MCH_HudItem#calc},
     * {@code configureCanvas}, {@code drawVarMap} and every {@code execute}, with the GL calls
     * replaced by the recorder.
     */
    static final class Upstream {

        private static final double DEFAULT_CANVAS_WIDTH = 427.0;
        private static final double DEFAULT_CANVAS_HEIGHT = 240.0;
        private static final AviatorEvaluatorInstance AVIATOR = AviatorEvaluator.newInstance();
        private static final Map<String, Expression> EXPR_CACHE = new HashMap<>();
        private static final String[] AZIMUTH_8 = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        private static final int AZIMUTH_8_ANG = 360 / AZIMUTH_8.length;
        private static final float[] SIN_TABLE = new float[65536];

        static {
            for (int i = 0; i < 65536; ++i) {
                SIN_TABLE[i] = (float) Math.sin((double) i * Math.PI * 2.0D / 65536.0D);
            }
        }

        private static final class HudState {
            boolean isIfFalse;
            boolean exit;
            boolean isDrawing;
        }

        private final Function<String, Hud> manager;
        private final Map<Hud, HudState> states = new IdentityHashMap<>();
        int colorSetting = -16777216;
        private double width;
        private double height;
        private double centerX;
        private double centerY;
        private double hudScaleX = 1.0;
        private double hudScaleY = 1.0;
        private boolean hudAutoScaled;
        private boolean hudAutoScaleRequested;
        private Map<String, Object> varMap;
        private HudFrame frame;
        private Recorder rec;

        Upstream(Function<String, Hud> manager) {
            this.manager = manager;
        }

        private HudState state(Hud hud) {
            return this.states.computeIfAbsent(hud, h -> new HudState());
        }

        List<String> draw(Hud hud, HudFrame frame) {
            this.frame = frame;
            this.rec = new Recorder();
            this.width = frame.width;
            this.height = frame.height;
            boolean automaticallyScaled = frame.autoScale;
            this.configureCanvas(automaticallyScaled && !hud.ignoreAutoScale, automaticallyScaled);
            HudState s = this.state(hud);
            s.isIfFalse = false;
            s.isDrawing = false;
            s.exit = false;
            this.updateVarMap();
            this.drawItems(hud);
            this.drawVarMap();
            return this.rec.calls;
        }

        private void configureCanvas(boolean automaticallyScaled, boolean autoScaleRequested) {
            this.centerX = this.width / 2.0;
            this.centerY = this.height / 2.0;
            this.hudAutoScaled = automaticallyScaled;
            this.hudAutoScaleRequested = autoScaleRequested;
            this.hudScaleX = automaticallyScaled ? this.width / DEFAULT_CANVAS_WIDTH : 1.0;
            this.hudScaleY = automaticallyScaled ? this.height / DEFAULT_CANVAS_HEIGHT : 1.0;
        }

        private double resolveHudX(double offsetFromCenter) {
            return this.centerX + offsetFromCenter * this.hudScaleX;
        }

        private double resolveHudY(double offsetFromCenter) {
            return this.centerY + offsetFromCenter * this.hudScaleY;
        }

        private void updateVarMap() {
            this.varMap = new LinkedHashMap<>();
            this.varMap.put("color", this.getColor());
            this.varMap.put("center_x", this.centerX);
            this.varMap.put("center_y", this.centerY);
            this.varMap.put("width", this.width);
            this.varMap.put("height", this.height);
            this.frame.vars.forEach(this.varMap::put);
        }

        private double getColor() {
            long l = this.colorSetting;
            l &= -1L;
            return l;
        }

        private double calc(String formula) {
            Expression expr = EXPR_CACHE.computeIfAbsent(formula, form -> AVIATOR.compile(form, true));
            Object result = expr.execute(new HashMap<>(this.varMap));
            if (result instanceof Number num) {
                return num.doubleValue();
            } else if (result instanceof Boolean bool) {
                return bool ? 1 : 0;
            }
            throw new RuntimeException("Expression returned unsupported type: " + result);
        }

        private void drawItems(Hud hud) {
            HudState s = this.state(hud);
            if (!s.isDrawing) {
                s.isDrawing = true;
                for (HudItem item : hud.list) {
                    boolean canExecute = item instanceof HudItem.Conditional || !s.isIfFalse;
                    if (canExecute) {
                        this.execute(item, hud);
                        if (s.exit) {
                            break;
                        }
                    }
                }
                s.exit = false;
                s.isIfFalse = false;
                s.isDrawing = false;
            }
        }

        private void execute(HudItem item, Hud parent) {
            switch (item) {
                case HudItem.Call i -> {
                    Hud hud = this.manager.apply(i.hudName);
                    if (hud != null) {
                        boolean previousAutoScaled = this.hudAutoScaled;
                        boolean previousAutoScaleRequested = this.hudAutoScaleRequested;
                        boolean calledAutoScaled = previousAutoScaleRequested && !hud.ignoreAutoScale;
                        if (calledAutoScaled != previousAutoScaled) {
                            this.configureCanvas(calledAutoScaled, previousAutoScaleRequested);
                        }
                        this.drawItems(hud);
                        if (calledAutoScaled != previousAutoScaled) {
                            this.configureCanvas(previousAutoScaled, previousAutoScaleRequested);
                        }
                    }
                }
                case HudItem.CameraRot i -> this.drawCommonGunnerCamera(this.colorSetting,
                        this.resolveHudX(this.calc(i.drawPosX)), this.resolveHudY(this.calc(i.drawPosY)));
                case HudItem.Color i -> {
                    double d = this.calc(i.updateColor);
                    long l = (long) d;
                    this.colorSetting = (int) l;
                    this.varMap.put("color", this.getColor());
                }
                case HudItem.Conditional i -> {
                    if (!i.isEndif) {
                        this.state(parent).isIfFalse = this.calc(i.conditional) == 0.0;
                    } else {
                        this.state(parent).isIfFalse = false;
                    }
                }
                case HudItem.Exit i -> this.state(parent).exit = true;
                case HudItem.Graduation i -> this.graduation(i);
                case HudItem.Line i -> {
                    double[] lines = new double[i.pos.length];
                    int pairLength = lines.length - (lines.length % 2);
                    for (int k = 0; k < pairLength; k += 2) {
                        lines[k] = this.resolveHudX(this.calc(i.pos[k]));
                        lines[k + 1] = this.resolveHudY(this.calc(i.pos[k + 1]));
                    }
                    if (pairLength == lines.length) {
                        this.rec.line(lines, this.colorSetting, 3);
                    } else {
                        double[] safeLines = new double[pairLength];
                        System.arraycopy(lines, 0, safeLines, 0, pairLength);
                        this.rec.line(safeLines, this.colorSetting, 3);
                    }
                }
                case HudItem.LineStipple i -> {
                    double[] lines = new double[i.pos.length];
                    for (int k = 0; k < lines.length; k += 2) {
                        lines[k] = this.resolveHudX(this.calc(i.pos[k]));
                        lines[k + 1] = this.resolveHudY(this.calc(i.pos[k + 1]));
                    }
                    this.rec.lineStipple(lines, this.colorSetting, (int) this.calc(i.fac), (int) this.calc(i.pat));
                }
                case HudItem.Radar i -> {
                    double[] list = i.isEntityRadar ? this.frame.entityRadar : this.frame.enemyRadar;
                    if (list != null && list.length > 0) {
                        this.drawEntityList(list, (float) this.calc(i.rot), this.resolveHudX(this.calc(i.left)),
                                this.resolveHudY(this.calc(i.top)), this.calc(i.width), this.calc(i.height));
                    }
                }
                case HudItem.Rect i -> {
                    double x2 = this.resolveHudX(this.calc(i.left));
                    double y2 = this.resolveHudY(this.calc(i.top));
                    double x1 = x2 + (int) this.calc(i.width);
                    double y1 = y2 + (int) this.calc(i.height);
                    this.rec.rect(x1, y1, x2, y2, this.colorSetting);
                }
                case HudItem.Str i -> this.string(i);
                case HudItem.Texture i -> this.rec.texture(i.name, this.resolveHudX(this.calc(i.left)),
                        this.resolveHudY(this.calc(i.top)), this.calc(i.width), this.calc(i.height),
                        this.calc(i.uLeft), this.calc(i.vTop), this.calc(i.uWidth), this.calc(i.vHeight),
                        (float) this.calc(i.rot));
            }
        }

        private void string(HudItem.Str item) {
            int x = (int) this.resolveHudX(this.calc(item.posX));
            int y = (int) this.resolveHudY(this.calc(item.posY));
            Object[] prm = new Object[item.args.length];
            boolean currentWeapon = this.frame.hasWeapon;
            for (int i = 0; i < prm.length; i++) {
                switch (item.args[i]) {
                    case WPN_NAME -> {
                        prm[i] = this.frame.stringArgs.get(HudStringArg.WPN_NAME);
                        if (!currentWeapon) {
                            return;
                        }
                    }
                    case WPN_AMMO, WPN_RM_AMMO -> {
                        prm[i] = this.frame.stringArgs.get(item.args[i]);
                        if (!currentWeapon) {
                            return;
                        }
                        if (this.frame.weaponMagSize <= 0) {
                            return;
                        }
                    }
                    case RELOAD_PER, RELOAD_SEC, MORTAR_DIST -> {
                        prm[i] = this.frame.stringArgs.get(item.args[i]);
                        if (!currentWeapon) {
                            return;
                        }
                    }
                    case NONE -> {
                    }
                    default -> prm[i] = this.frame.stringArgs.get(item.args[i]);
                }
            }
            this.rec.text(String.format(item.format, prm), x, y, this.colorSetting, item.isCenteredString);
        }

        private void drawCommonGunnerCamera(int color, double posX, double posY) {
            double[] line = new double[] {posX - 21.0, posY - 11.0, posX + 21.0, posY - 11.0, posX + 21.0,
                    posY + 11.0, posX - 21.0, posY + 11.0};
            this.rec.line(line, color, 2);
            line = new double[] {posX - 21.0, posY, posX, posY, posX + 21.0, posY, posX, posY, posX, posY - 11.0,
                    posX, posY, posX, posY + 11.0, posX, posY};
            this.rec.lineStipple(line, color, 1, 52428);
            float pitch = this.frame.cameraPitch;
            if (pitch < -30.0F) {
                pitch = -30.0F;
            }
            if (pitch > 70.0F) {
                pitch = 70.0F;
            }
            pitch -= 20.0F;
            pitch = (float) (pitch * 0.16);
            float yaw = (float) getRotateDiff(this.frame.aircraftYaw, this.frame.cameraYaw);
            yaw *= 2.0F;
            if (yaw < -50.0F) {
                yaw = -50.0F;
            }
            if (yaw > 50.0F) {
                yaw = 50.0F;
            }
            yaw = (float) (yaw * 0.34);
            line = new double[] {posX + yaw - 3.0, posY + pitch - 2.0, posX + yaw + 3.0, posY + pitch - 2.0,
                    posX + yaw + 3.0, posY + pitch + 2.0, posX + yaw - 3.0, posY + pitch + 2.0};
            this.rec.line(line, color, 2);
        }

        private void drawEntityList(double[] src, float r, double left, double top, double w, double h) {
            double w1 = -w / 2.0;
            double w2 = w / 2.0;
            double h1 = -h / 2.0;
            double h2 = h / 2.0;
            double wFactor = w / 64.0;
            double hFactor = h / 64.0;
            double[] list = new double[src.length];
            int idx = 0;
            for (int v = 0; v < src.length; v += 2) {
                list[idx] = src[v] / 2.0 * wFactor;
                list[idx + 1] = src[v + 1] / 2.0 * hFactor;
                idx += 2;
            }
            r = r / 180.0F * (float) Math.PI;
            for (int i = 0; i + 1 < list.length; i += 2) {
                double x = list[i];
                double y = list[i + 1];
                list[i] = x * cos(r) - y * sin(r);
                list[i + 1] = x * sin(r) + y * cos(r);
            }
            ArrayList<Double> drawList = new ArrayList<>();
            for (int i = 0; i + 1 < list.length; i += 2) {
                if (list[i] > w1 && list[i] < w2 && list[i + 1] > h1 && list[i + 1] < h2) {
                    drawList.add(list[i] + left + w / 2.0);
                    drawList.add(list[i + 1] + top + h / 2.0);
                }
            }
            double[] points = new double[drawList.size()];
            for (int i = 0; i < points.length; i++) {
                points[i] = drawList.get(i);
            }
            this.rec.points(points, this.colorSetting);
        }

        private static float sin(float value) {
            return SIN_TABLE[(int) (value * 10430.378F) & 65535];
        }

        private static float cos(float value) {
            return SIN_TABLE[(int) (value * 10430.378F + 16384.0F) & 65535];
        }

        private void graduation(HudItem.Graduation item) {
            int x = (int) this.resolveHudX(this.calc(item.drawPosX));
            int y = (int) this.resolveHudY(this.calc(item.drawPosY));
            this.rec.pushRotation(x, y, (float) this.calc(item.drawRoll));
            if (item.type == 0) {
                this.drawCommonGraduationYaw(this.calc(item.drawRot), this.colorSetting, x, y);
            } else if (item.type == 1) {
                this.drawCommonGraduationPitch1(this.calc(item.drawRot), this.colorSetting, x, y);
            } else if (item.type == 2 || item.type == 3) {
                this.drawCommonGraduationPitch2(item.type, this.calc(item.drawRot), this.colorSetting, x, y);
            }
            this.rec.popRotation();
        }

        private void drawCommonGraduationPitch2(int type, double playerPitch, int color, int posX, int posY) {
            playerPitch = -playerPitch;
            int pitch_n = (int) playerPitch / 5 * 5;
            double[] line = new double[8];
            int start = type == 2 ? 0 : 1;
            int end = type == 2 ? 5 : 4;
            int INT = type == 2 ? 1 : 2;
            for (int i = start; i < end; i++) {
                int pitch = -(-pitch_n - 10 + i * 5);
                double p_rest = playerPitch % 5.0;
                int x = pitch != 0 ? 50 : 100;
                int y = posY + (int) (-60 * INT + p_rest * 6.0 * INT + i * 30 * INT);
                line[0] = posX - x;
                line[1] = y + (pitch > 0 ? 2 : (pitch == 0 ? 0 : -2));
                line[2] = posX - 50;
                line[3] = y;
                line[4] = posX + x;
                line[5] = line[1];
                line[6] = posX + 50;
                line[7] = y;
                this.rec.line(line, color, 1);
                line[0] = posX - 50;
                line[1] = y;
                line[2] = posX - 30;
                line[3] = y;
                line[4] = posX + 50;
                line[5] = y;
                line[6] = posX + 30;
                line[7] = y;
                if (pitch >= 0) {
                    this.rec.line(line, color, 1);
                } else {
                    this.rec.lineStipple(line, color, 1, 52428);
                }
                if (pitch != 0) {
                    this.rec.text("" + pitch, posX - 50 - 10, y - 4, color, true);
                    this.rec.text("" + pitch, posX + 50 + 10, y - 4, color, true);
                }
            }
        }

        private void drawCommonGraduationPitch1(double playerPitch, int color, int posX, int posY) {
            int pitch = (int) playerPitch % 360;
            int y = (int) (playerPitch * 10.0 % 10.0);
            if (y < 0) {
                y += 10;
            }
            int posX_L = posX - 100;
            int posX_R = posX + 100;
            posY -= 80;
            double[] line = new double[144];
            int p = !(playerPitch >= 0.0) && y != 0 ? pitch - 9 : pitch - 8;
            for (int i = 0; i < line.length / 8; p++) {
                int olx = p % 3 == 0 ? 15 : 5;
                int ilx = 0;
                line[i * 8] = posX_L - olx;
                line[i * 8 + 1] = posY + i * 10 - y;
                line[i * 8 + 2] = posX_L + ilx;
                line[i * 8 + 3] = posY + i * 10 - y;
                line[i * 8 + 4] = posX_R + olx;
                line[i * 8 + 5] = posY + i * 10 - y;
                line[i * 8 + 6] = posX_R - ilx;
                line[i * 8 + 7] = posY + i * 10 - y;
                i++;
            }
            this.rec.line(line, color, 1);
            double[] verticalLine = new double[] {posX_L - 25, posY - 90, posX_L, posY - 90, posX_L, posY + 90,
                    posX_L - 25, posY + 90};
            this.rec.line(verticalLine, color, 3);
            verticalLine = new double[] {posX_R + 25, posY - 90, posX_R, posY - 90, posX_R, posY + 90, posX_R + 25,
                    posY + 90};
            this.rec.line(verticalLine, color, 3);
        }

        private void drawCommonGraduationYaw(double playerYaw, int color, int posX, int posY) {
            double yaw = getRotate360(playerYaw);
            posX -= 90;
            double[] line = new double[76];
            int x = (int) (yaw * 10.0) % 10;
            int y = (int) yaw - 9;
            for (int i = 0; i < line.length / 4; y++) {
                int azPosX = posX + i * 10 - x;
                line[i * 4] = azPosX;
                line[i * 4 + 1] = posY;
                line[i * 4 + 2] = azPosX;
                line[i * 4 + 3] = posY + (y % 3 == 0 ? 10 : 5);
                if (y % 45 == 0) {
                    this.rec.text(getAzimuthStr8(y), azPosX, posY - 10, -65536, true);
                } else if (y % 3 == 0) {
                    int rot = y + 180;
                    if (rot < 0) {
                        rot += 360;
                    }
                    if (rot > 360) {
                        rot -= 360;
                    }
                    this.rec.text(String.format("%d", rot), azPosX, posY - 10, color, true);
                }
                i++;
            }
            this.rec.line(line, color, 1);
        }

        private void drawVarMap() {
            if (!this.frame.testMode || this.varMap.isEmpty()) {
                return;
            }
            var screenWidth = this.centerX * 2;
            var screenHeight = this.centerY * 2;
            var leftColX = Math.max(10, (int) (screenWidth * 0.10));
            var rightColX = Math.min(screenWidth - 150, (int) (screenWidth * 0.80));
            var startY = Math.max(10, (int) (screenHeight * 0.35));
            var valOffsetX = Math.max(80, (int) (screenWidth * 0.01));
            var x = leftColX;
            var y = startY;
            var halfSize = this.varMap.size() / 2;
            var i = 0;
            for (var entry : this.varMap.entrySet()) {
                var key = entry.getKey();
                var val = entry.getValue();
                String fmt;
                if (key.equalsIgnoreCase("color")) {
                    var color = val instanceof Number n ? n.intValue() : 0;
                    fmt = ": 0x%08X".formatted(color);
                } else if (val instanceof Boolean b) {
                    fmt = ": %s (%.2f)".formatted(b, b ? 1.0 : 0.0);
                } else if (val instanceof Number n) {
                    fmt = ": %.2f".formatted(n.doubleValue());
                } else {
                    fmt = ": " + val;
                }
                this.rec.text(key, x, y, 52992, false);
                this.rec.text(fmt, x + valOffsetX, y, 52992, false);
                i++;
                y += 10;
                if (i == halfSize) {
                    x = (int) rightColX;
                    y = startY;
                }
            }
        }

        private static double getRotate360(double r) {
            r %= 360.0;
            return r >= 0.0 ? r : r + 360.0;
        }

        private static double getRotateDiff(double base, double target) {
            base = getRotate360(base);
            target = getRotate360(target);
            if (target - base < -180.0) {
                target += 360.0;
            } else if (target - base > 180.0) {
                base += 360.0;
            }
            return target - base;
        }

        private static String getAzimuthStr8(int dir) {
            dir %= 360;
            if (dir < 0) {
                dir += 360;
            }
            dir /= AZIMUTH_8_ANG;
            return AZIMUTH_8[dir];
        }
    }
}
