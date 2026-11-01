package io.github.mcheliceport.client.hud;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.content.hud.Hud;
import io.github.mcheliceport.content.hud.HudItem;
import io.github.mcheliceport.content.hud.HudStringArg;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Runs HUD scripts: the {@code execute} methods of upstream's {@code MCH_HudItem} subclasses and
 * {@code MCH_Hud#draw}/{@code drawItems}, drawing on a {@link HudCanvas}. Takes no Minecraft
 * type, so the tests run it against upstream's code with a recording canvas.
 *
 * <p>Upstream kept this state in static fields; one interpreter per game holds it here. As
 * upstream, the colour set by {@code Color} carries over from one frame to the next, an
 * {@code If} is not nested (each {@code If} replaces the condition, each {@code Endif} clears
 * it), {@code Exit} leaves only the HUD it is in, and a HUD that is already being drawn cannot be
 * called again.
 *
 * <p>Upstream stopped the game with an exception when an expression could not be evaluated or a
 * string could not be formatted. The port logs the first error of each item, skips the item (an
 * {@code If} that fails counts as false) and goes on.
 */
public final class HudInterpreter {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double DEFAULT_CANVAS_WIDTH = 427.0;
    private static final double DEFAULT_CANVAS_HEIGHT = 240.0;
    private static final String[] AZIMUTH_8 = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final int AZIMUTH_8_ANG = 360 / AZIMUTH_8.length;
    /** {@code 0xCCCC}, the dash pattern of the camera cross and the negative pitch bars. */
    private static final int DASHES = 52428;
    private static final float[] SIN_TABLE = new float[65536];

    static {
        for (int i = 0; i < SIN_TABLE.length; i++) {
            SIN_TABLE[i] = (float) Math.sin(i * Math.PI * 2.0 / 65536.0);
        }
    }

    private final Function<String, @Nullable Hud> huds;
    private final Set<Hud> drawing = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<String> reported = new HashSet<>();
    /** Upstream's {@code colorSetting}. */
    private int color = 0xFF000000;
    private int errorCount;

    private HudFrame frame = new HudFrame();
    private HudCanvas canvas = NO_CANVAS;
    private final Map<String, Object> vars = new LinkedHashMap<>();
    private final HudExpression.Variables variables = name -> HudExpression.normalize(this.vars.get(name));
    private double centerX;
    private double centerY;
    private double scaleX = 1.0;
    private double scaleY = 1.0;
    private boolean autoScaled;
    private boolean autoScaleRequested;

    /** @param huds looks a HUD up by name for {@code Call} */
    public HudInterpreter(Function<String, @Nullable Hud> huds) {
        this.huds = huds;
    }

    /** The current colour (upstream's {@code colorSetting}). */
    public int color() {
        return this.color;
    }

    /** Number of item errors so far, for tests. */
    public int errorCount() {
        return this.errorCount;
    }

    /** The variables of the last frame, in upstream's order, for tests and the debug view. */
    public Map<String, Object> vars() {
        return Collections.unmodifiableMap(this.vars);
    }

    /** Upstream {@code MCH_Hud#draw}: one frame of a HUD, then the {@code TestMode} variable list. */
    public void draw(Hud hud, HudFrame frame, HudCanvas canvas) {
        this.frame = frame;
        this.canvas = canvas;
        this.configureCanvas(frame.autoScale && !hud.ignoreAutoScale, frame.autoScale);
        this.vars.clear();
        this.vars.put("color", this.colorVar());
        this.vars.put("center_x", this.centerX);
        this.vars.put("center_y", this.centerY);
        this.vars.put("width", frame.width);
        this.vars.put("height", frame.height);
        frame.vars.forEach(this.vars::putIfAbsent);
        this.drawItems(hud);
        this.drawVarMap();
        this.canvas = NO_CANVAS;
    }

    private void configureCanvas(boolean automaticallyScaled, boolean requested) {
        this.centerX = this.frame.width / 2.0;
        this.centerY = this.frame.height / 2.0;
        this.autoScaled = automaticallyScaled;
        this.autoScaleRequested = requested;
        this.scaleX = automaticallyScaled ? this.frame.width / DEFAULT_CANVAS_WIDTH : 1.0;
        this.scaleY = automaticallyScaled ? this.frame.height / DEFAULT_CANVAS_HEIGHT : 1.0;
    }

    private double hudX(double offsetFromCenter) {
        return this.centerX + offsetFromCenter * this.scaleX;
    }

    private double hudY(double offsetFromCenter) {
        return this.centerY + offsetFromCenter * this.scaleY;
    }

    private double calc(String formula) {
        return HudExpression.calc(formula, this.variables);
    }

    private double colorVar() {
        return (double) (long) this.color;
    }

    /** Upstream {@code MCH_Hud#drawItems}. */
    private void drawItems(Hud hud) {
        if (!this.drawing.add(hud)) {
            return;
        }
        try {
            boolean ifFalse = false;
            for (int index = 0; index < hud.list.size(); index++) {
                HudItem item = hud.list.get(index);
                if (item instanceof HudItem.Conditional cond) {
                    if (cond.isEndif) {
                        ifFalse = false;
                    } else {
                        try {
                            ifFalse = this.calc(cond.conditional) == 0.0;
                        } catch (RuntimeException e) {
                            this.report(hud, index, item, e);
                            ifFalse = true;
                        }
                    }
                    continue;
                }
                if (ifFalse) {
                    continue;
                }
                if (item instanceof HudItem.Exit) {
                    break;
                }
                try {
                    this.execute(item);
                } catch (RuntimeException e) {
                    this.report(hud, index, item, e);
                }
            }
        } finally {
            this.drawing.remove(hud);
        }
    }

    private void report(Hud hud, int index, HudItem item, RuntimeException e) {
        this.errorCount++;
        if (this.reported.add(hud.name + "#" + index)) {
            String where = item.fileLine > 0 ? "line " + item.fileLine : "item " + (index + 1);
            LOGGER.error("HUD '{}' ({}), {}: {}", hud.name, hud.fileName, where, e.toString());
        }
    }

    private void execute(HudItem item) {
        switch (item) {
            case HudItem.Call i -> this.call(i);
            case HudItem.CameraRot i -> this.cameraRot(i);
            case HudItem.Color i -> {
                double d = this.calc(i.updateColor);
                this.color = (int) (long) d;
                this.vars.put("color", this.colorVar());
            }
            case HudItem.Graduation i -> this.graduation(i);
            case HudItem.Line i -> this.line(i);
            case HudItem.LineStipple i -> this.lineStipple(i);
            case HudItem.Radar i -> this.radar(i);
            case HudItem.Rect i -> {
                double x2 = this.hudX(this.calc(i.left));
                double y2 = this.hudY(this.calc(i.top));
                double x1 = x2 + (int) this.calc(i.width);
                double y1 = y2 + (int) this.calc(i.height);
                this.canvas.rect(x1, y1, x2, y2, this.color);
            }
            case HudItem.Str i -> this.string(i);
            case HudItem.Texture i -> this.canvas.texture(i.name, this.hudX(this.calc(i.left)),
                    this.hudY(this.calc(i.top)), this.calc(i.width), this.calc(i.height), this.calc(i.uLeft),
                    this.calc(i.vTop), this.calc(i.uWidth), this.calc(i.vHeight), (float) this.calc(i.rot));
            case HudItem.Conditional i -> throw new IllegalStateException("handled by drawItems");
            case HudItem.Exit i -> throw new IllegalStateException("handled by drawItems");
        }
    }

    /** Upstream {@code MCH_HudItemCall#execute}. */
    private void call(HudItem.Call item) {
        Hud hud = this.huds.apply(item.hudName);
        if (hud == null) {
            return;
        }
        boolean previousAutoScaled = this.autoScaled;
        boolean previousRequested = this.autoScaleRequested;
        boolean calledAutoScaled = previousRequested && !hud.ignoreAutoScale;
        if (calledAutoScaled != previousAutoScaled) {
            this.configureCanvas(calledAutoScaled, previousRequested);
        }
        try {
            this.drawItems(hud);
        } finally {
            if (calledAutoScaled != previousAutoScaled) {
                this.configureCanvas(previousAutoScaled, previousRequested);
            }
        }
    }

    /** Upstream {@code MCH_HudItemLine#execute}: a line strip; an odd last value is ignored. */
    private void line(HudItem.Line item) {
        int pairLength = item.pos.length - item.pos.length % 2;
        double[] lines = new double[pairLength];
        for (int i = 0; i < pairLength; i += 2) {
            lines[i] = this.hudX(this.calc(item.pos[i]));
            lines[i + 1] = this.hudY(this.calc(item.pos[i + 1]));
        }
        this.canvas.line(lines, this.color, HudCanvas.LINE_STRIP);
    }

    /** Upstream {@code MCH_HudItemLineStipple#execute}. */
    private void lineStipple(HudItem.LineStipple item) {
        if (item.pos.length % 2 != 0) {
            throw new IllegalArgumentException("DrawLineStipple needs x, y pairs, got " + item.pos.length + " values");
        }
        double[] lines = new double[item.pos.length];
        for (int i = 0; i < lines.length; i += 2) {
            lines[i] = this.hudX(this.calc(item.pos[i]));
            lines[i + 1] = this.hudY(this.calc(item.pos[i + 1]));
        }
        this.canvas.lineStipple(lines, this.color, (int) this.calc(item.fac), (int) this.calc(item.pat));
    }

    /** Upstream {@code MCH_HudItemRadar#execute} and {@code drawEntityList}. */
    private void radar(HudItem.Radar item) {
        double[] src = item.isEntityRadar ? this.frame.entityRadar : this.frame.enemyRadar;
        if (src == null || src.length == 0) {
            return;
        }
        float r = (float) this.calc(item.rot);
        double left = this.hudX(this.calc(item.left));
        double top = this.hudY(this.calc(item.top));
        double w = this.calc(item.width);
        double h = this.calc(item.height);
        double w1 = -w / 2.0;
        double w2 = w / 2.0;
        double h1 = -h / 2.0;
        double h2 = h / 2.0;
        double wFactor = w / 64.0;
        double hFactor = h / 64.0;
        double[] list = new double[src.length - src.length % 2];
        for (int i = 0; i < list.length; i += 2) {
            list[i] = src[i] / 2.0 * wFactor;
            list[i + 1] = src[i + 1] / 2.0 * hFactor;
        }
        rotatePoints(list, r);
        double[] draw = new double[list.length];
        int n = 0;
        for (int i = 0; i + 1 < list.length; i += 2) {
            if (list[i] > w1 && list[i] < w2 && list[i + 1] > h1 && list[i + 1] < h2) {
                draw[n++] = list[i] + left + w / 2.0;
                draw[n++] = list[i + 1] + top + h / 2.0;
            }
        }
        this.canvas.points(java.util.Arrays.copyOf(draw, n), this.color);
    }

    /** Upstream {@code MCH_HudItemString#execute}. */
    private void string(HudItem.Str item) {
        int x = (int) this.hudX(this.calc(item.posX));
        int y = (int) this.hudY(this.calc(item.posY));
        Object[] prm = new Object[item.args.length];
        for (int i = 0; i < prm.length; i++) {
            HudStringArg arg = item.args[i];
            prm[i] = this.frame.stringArgs.get(arg);
            switch (arg) {
                case WPN_NAME, RELOAD_PER, RELOAD_SEC, MORTAR_DIST -> {
                    if (!this.frame.hasWeapon) {
                        return;
                    }
                }
                case WPN_AMMO, WPN_RM_AMMO -> {
                    if (!this.frame.hasWeapon || this.frame.weaponMagSize <= 0) {
                        return;
                    }
                }
                default -> {
                }
            }
        }
        this.canvas.text(String.format(item.format, prm), x, y, this.color, item.isCenteredString);
    }

    /** Upstream {@code MCH_HudItemCameraRot#execute} and {@code drawCommonGunnerCamera}. */
    private void cameraRot(HudItem.CameraRot item) {
        double posX = this.hudX(this.calc(item.drawPosX));
        double posY = this.hudY(this.calc(item.drawPosY));
        int color = this.color;
        this.canvas.line(new double[] {posX - 21.0, posY - 11.0, posX + 21.0, posY - 11.0, posX + 21.0, posY + 11.0,
                posX - 21.0, posY + 11.0}, color, HudCanvas.LINE_LOOP);
        this.canvas.lineStipple(new double[] {posX - 21.0, posY, posX, posY, posX + 21.0, posY, posX, posY, posX,
                posY - 11.0, posX, posY, posX, posY + 11.0, posX, posY}, color, 1, DASHES);
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
        this.canvas.line(new double[] {posX + yaw - 3.0, posY + pitch - 2.0, posX + yaw + 3.0, posY + pitch - 2.0,
                posX + yaw + 3.0, posY + pitch + 2.0, posX + yaw - 3.0, posY + pitch + 2.0}, color,
                HudCanvas.LINE_LOOP);
    }

    /** Upstream {@code MCH_HudItemGraduation#execute}. */
    private void graduation(HudItem.Graduation item) {
        int x = (int) this.hudX(this.calc(item.drawPosX));
        int y = (int) this.hudY(this.calc(item.drawPosY));
        this.canvas.pushRotation(x, y, (float) this.calc(item.drawRoll));
        try {
            if (item.type == 0) {
                this.graduationYaw(this.calc(item.drawRot), this.color, x, y);
            } else if (item.type == 1) {
                this.graduationPitch1(this.calc(item.drawRot), this.color, x, y);
            } else if (item.type == 2 || item.type == 3) {
                this.graduationPitch2(item.type, this.calc(item.drawRot), this.color, x, y);
            }
        } finally {
            this.canvas.popRotation();
        }
    }

    private void graduationPitch2(int type, double playerPitch, int color, int posX, int posY) {
        playerPitch = -playerPitch;
        int pitchN = (int) playerPitch / 5 * 5;
        double[] line = new double[8];
        int start = type == 2 ? 0 : 1;
        int end = type == 2 ? 5 : 4;
        int interval = type == 2 ? 1 : 2;
        for (int i = start; i < end; i++) {
            int pitch = -(-pitchN - 10 + i * 5);
            double pRest = playerPitch % 5.0;
            int x = pitch != 0 ? 50 : 100;
            int y = posY + (int) (-60 * interval + pRest * 6.0 * interval + i * 30 * interval);
            line[0] = posX - x;
            line[1] = y + (pitch > 0 ? 2 : (pitch == 0 ? 0 : -2));
            line[2] = posX - 50;
            line[3] = y;
            line[4] = posX + x;
            line[5] = line[1];
            line[6] = posX + 50;
            line[7] = y;
            this.canvas.line(line.clone(), color, HudCanvas.LINES);
            line[0] = posX - 50;
            line[1] = y;
            line[2] = posX - 30;
            line[3] = y;
            line[4] = posX + 50;
            line[5] = y;
            line[6] = posX + 30;
            line[7] = y;
            if (pitch >= 0) {
                this.canvas.line(line.clone(), color, HudCanvas.LINES);
            } else {
                this.canvas.lineStipple(line.clone(), color, 1, DASHES);
            }
            if (pitch != 0) {
                this.canvas.text("" + pitch, posX - 50 - 10, y - 4, color, true);
                this.canvas.text("" + pitch, posX + 50 + 10, y - 4, color, true);
            }
        }
    }

    private void graduationPitch1(double playerPitch, int color, int posX, int posY) {
        int pitch = (int) playerPitch % 360;
        int y = (int) (playerPitch * 10.0 % 10.0);
        if (y < 0) {
            y += 10;
        }
        int posXL = posX - 100;
        int posXR = posX + 100;
        posY -= 80;
        double[] line = new double[144];
        int p = !(playerPitch >= 0.0) && y != 0 ? pitch - 9 : pitch - 8;
        for (int i = 0; i < line.length / 8; p++) {
            int olx = p % 3 == 0 ? 15 : 5;
            int ilx = 0;
            line[i * 8] = posXL - olx;
            line[i * 8 + 1] = posY + i * 10 - y;
            line[i * 8 + 2] = posXL + ilx;
            line[i * 8 + 3] = posY + i * 10 - y;
            line[i * 8 + 4] = posXR + olx;
            line[i * 8 + 5] = posY + i * 10 - y;
            line[i * 8 + 6] = posXR - ilx;
            line[i * 8 + 7] = posY + i * 10 - y;
            i++;
        }
        this.canvas.line(line, color, HudCanvas.LINES);
        this.canvas.line(new double[] {posXL - 25, posY - 90, posXL, posY - 90, posXL, posY + 90, posXL - 25,
                posY + 90}, color, HudCanvas.LINE_STRIP);
        this.canvas.line(new double[] {posXR + 25, posY - 90, posXR, posY - 90, posXR, posY + 90, posXR + 25,
                posY + 90}, color, HudCanvas.LINE_STRIP);
    }

    private void graduationYaw(double playerYaw, int color, int posX, int posY) {
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
                this.canvas.text(azimuthStr8(y), azPosX, posY - 10, 0xFFFF0000, true);
            } else if (y % 3 == 0) {
                int rot = y + 180;
                if (rot < 0) {
                    rot += 360;
                }
                if (rot > 360) {
                    rot -= 360;
                }
                this.canvas.text(String.format("%d", rot), azPosX, posY - 10, color, true);
            }
            i++;
        }
        this.canvas.line(line, color, HudCanvas.LINES);
    }

    /** Upstream {@code MCH_HudItem#drawVarMap}: with {@code TestMode}, every variable in two columns. */
    private void drawVarMap() {
        if (!this.frame.testMode || this.vars.isEmpty()) {
            return;
        }
        double screenWidth = this.centerX * 2;
        double screenHeight = this.centerY * 2;
        int leftColX = Math.max(10, (int) (screenWidth * 0.10));
        double rightColX = Math.min(screenWidth - 150, (int) (screenWidth * 0.80));
        int startY = Math.max(10, (int) (screenHeight * 0.35));
        int valOffsetX = Math.max(80, (int) (screenWidth * 0.01));
        int x = leftColX;
        int y = startY;
        int halfSize = this.vars.size() / 2;
        int i = 0;
        for (Map.Entry<String, Object> entry : this.vars.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            String fmt;
            if (key.equalsIgnoreCase("color")) {
                int c = val instanceof Number n ? n.intValue() : 0;
                fmt = ": 0x%08X".formatted(c);
            } else if (val instanceof Boolean b) {
                fmt = ": %s (%.2f)".formatted(b, b ? 1.0 : 0.0);
            } else if (val instanceof Number n) {
                fmt = ": %.2f".formatted(n.doubleValue());
            } else {
                fmt = ": " + val;
            }
            this.canvas.text(key, x, y, 52992, false);
            this.canvas.text(fmt, x + valOffsetX, y, 52992, false);
            i++;
            y += 10;
            if (i == halfSize) {
                x = (int) rightColX;
                y = startY;
            }
        }
    }

    /** Upstream {@code MCH_Lib.getRotate360}. */
    static double getRotate360(double r) {
        r %= 360.0;
        return r >= 0.0 ? r : r + 360.0;
    }

    /** Upstream {@code MCH_Lib.getRotateDiff}. */
    static double getRotateDiff(double base, double target) {
        base = getRotate360(base);
        target = getRotate360(target);
        if (target - base < -180.0) {
            target += 360.0;
        } else if (target - base > 180.0) {
            base += 360.0;
        }
        return target - base;
    }

    /** Upstream {@code MCH_Lib.getAzimuthStr8}. */
    static String azimuthStr8(int dir) {
        dir %= 360;
        if (dir < 0) {
            dir += 360;
        }
        return AZIMUTH_8[dir / AZIMUTH_8_ANG];
    }

    /** Upstream {@code MCH_Lib.rotatePoints}, with 1.12's table-based {@code MathHelper} sine. */
    static void rotatePoints(double[] points, float r) {
        r = r / 180.0F * (float) Math.PI;
        float sin = SIN_TABLE[(int) (r * 10430.378F) & 65535];
        float cos = SIN_TABLE[(int) (r * 10430.378F + 16384.0F) & 65535];
        for (int i = 0; i + 1 < points.length; i += 2) {
            double x = points[i];
            double y = points[i + 1];
            points[i] = x * cos - y * sin;
            points[i + 1] = x * sin + y * cos;
        }
    }

    private static final HudCanvas NO_CANVAS = new HudCanvas() {
        @Override
        public void line(double[] xy, int color, int mode) {
        }

        @Override
        public void lineStipple(double[] xy, int color, int factor, int pattern) {
        }

        @Override
        public void rect(double x0, double y0, double x1, double y1, int color) {
        }

        @Override
        public void points(double[] xy, int color) {
        }

        @Override
        public void texture(String name, double left, double top, double width, double height, double uLeft,
                double vTop, double uWidth, double vHeight, float rot) {
        }

        @Override
        public void text(String text, int x, int y, int color, boolean centered) {
        }

        @Override
        public void pushRotation(double x, double y, float degrees) {
        }

        @Override
        public void popRotation() {
        }
    };
}
