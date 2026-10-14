package io.github.mcheliceport.content.hud;

import static io.github.mcheliceport.content.hud.HudFormula.toFormula;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * One command of a HUD script.
 *
 * <p>Ported from the data part of upstream's {@code MCH_HudItem} subclasses. The constructors
 * normalise expressions with {@link HudFormula#toFormula} exactly where upstream did. Drawing is
 * done by the client HUD renderer (M4).
 */
public abstract sealed class HudItem {

    /** Source line in a TXT file; 0 for YAML files. Only used in error messages. */
    public final int fileLine;

    protected HudItem(int fileLine) {
        this.fileLine = fileLine;
    }

    /** {@code Call}: runs another HUD by name. */
    public static final class Call extends HudItem {

        public final String hudName;

        public Call(int fileLine, String name) {
            super(fileLine);
            this.hudName = name;
        }
    }

    /** {@code CameraRot}: draws the camera direction indicator. */
    public static final class CameraRot extends HudItem {

        public final String drawPosX;
        public final String drawPosY;

        public CameraRot(int fileLine, String posx, String posy) {
            super(fileLine);
            this.drawPosX = toFormula(posx);
            this.drawPosY = toFormula(posy);
        }
    }

    /** {@code Color}: sets the drawing colour to the value of an expression (ARGB). */
    public static final class Color extends HudItem {

        public final String updateColor;

        public Color(int fileLine, String newColor) {
            super(fileLine);
            this.updateColor = newColor;
        }

        /** One ARGB expression, or four channel expressions (A, R, G, B); null for other counts. */
        public static @Nullable Color createByParams(int fileLine, String[] prm) {
            if (prm.length == 1) {
                return new Color(fileLine, toFormula(prm[0]));
            }
            return prm.length == 4
                    ? new Color(fileLine, "((" + toFormula(prm[0]) + ")<<24)|((" + toFormula(prm[1]) + ")<<16)|(("
                            + toFormula(prm[2]) + ")<<8 )|((" + toFormula(prm[3]) + ")<<0 )")
                    : null;
        }
    }

    /**
     * {@code If} or {@code Endif}. An {@code Endif} has an empty condition; upstream stored
     * {@code ""} for TXT files and {@code "0"} for YAML files, which made no difference.
     */
    public static final class Conditional extends HudItem {

        public final boolean isEndif;
        public final String conditional;

        public Conditional(int fileLine, boolean isEndif, @Nullable String conditional) {
            super(fileLine);
            this.isEndif = isEndif;
            this.conditional = isEndif ? "" : toFormula(conditional);
        }
    }

    /** {@code Exit}: stops drawing this HUD for the frame. */
    public static final class Exit extends HudItem {

        public Exit(int fileLine) {
            super(fileLine);
        }
    }

    /** {@code DrawGraduation}: pitch or yaw ladder. Type 0 yaw, 1 to 3 the pitch variants. */
    public static final class Graduation extends HudItem {

        public final String drawRot;
        public final String drawRoll;
        public final String drawPosX;
        public final String drawPosY;
        public final int type;

        public Graduation(int fileLine, int type, String rot, String roll, String posx, String posy) {
            super(fileLine);
            this.drawRot = toFormula(rot);
            this.drawRoll = toFormula(roll);
            this.drawPosX = toFormula(posx);
            this.drawPosY = toFormula(posy);
            this.type = type;
        }
    }

    /** {@code DrawLine}: a polyline through x, y expression pairs. */
    public static final class Line extends HudItem {

        public final String[] pos;

        public Line(int fileLine, String[] position) {
            super(fileLine);
            this.pos = new String[position.length];
            for (int i = 0; i < position.length; i++) {
                this.pos[i] = toFormula(position[i]);
            }
        }
    }

    /** {@code DrawLineStipple}: a dashed polyline with a 16-bit pattern and repeat factor. */
    public static final class LineStipple extends HudItem {

        public final String pat;
        public final String fac;
        public final String[] pos;

        /** YAML form: values already converted. */
        public LineStipple(int fileLine, String pat, String fac, String[] pos) {
            super(fileLine);
            this.pat = pat;
            this.fac = fac;
            this.pos = pos;
        }

        /**
         * TXT form: pattern, factor, then the x, y pairs. Upstream left the pattern and factor
         * unconverted here (so {@code 0xCCCC} kept its case); converting them as the YAML form
         * does gives the same values.
         */
        public LineStipple(int fileLine, String[] position) {
            super(fileLine);
            this.pat = toFormula(position[0]);
            this.fac = toFormula(position[1]);
            this.pos = new String[position.length - 2];
            for (int i = 0; i < position.length - 2; i++) {
                this.pos[i] = toFormula(position[2 + i]);
            }
        }
    }

    /** {@code DrawEntityRadar} / {@code DrawEnemyRadar}. */
    public static final class Radar extends HudItem {

        public final String rot;
        public final String left;
        public final String top;
        public final String width;
        public final String height;
        public final boolean isEntityRadar;

        public Radar(int fileLine, boolean isEntityRadar, String rot, String left, String top,
                @Nullable String width, @Nullable String height) {
            super(fileLine);
            this.isEntityRadar = isEntityRadar;
            this.rot = toFormula(rot);
            this.left = toFormula(left);
            this.top = toFormula(top);
            this.width = toFormula(width);
            this.height = toFormula(height);
        }
    }

    /** {@code DrawRect}: a filled rectangle. */
    public static final class Rect extends HudItem {

        public final String left;
        public final String top;
        public final String width;
        public final String height;

        public Rect(int fileLine, String left, String top, @Nullable String width, @Nullable String height) {
            super(fileLine);
            this.left = toFormula(left);
            this.top = toFormula(top);
            this.width = toFormula(width);
            this.height = toFormula(height);
        }
    }

    /** {@code DrawString} / {@code DrawCenteredString}: formatted text. */
    public static final class Str extends HudItem {

        public final String posX;
        public final String posY;
        public final String format;
        public final HudStringArg[] args;
        public final boolean isCenteredString;

        /**
         * @param arg all parameters as in the TXT line: x, y, format, then the argument names; only
         *            the names (index 3 on) are used
         */
        public Str(int fileLine, String posx, String posy, String fmt, String[] arg, boolean centered) {
            super(fileLine);
            this.posX = toFormula(posx);
            this.posY = toFormula(posy);
            this.format = fmt;
            int len = arg.length < 3 ? 0 : arg.length - 3;
            this.args = new HudStringArg[len];
            for (int i = 0; i < len; i++) {
                this.args[i] = HudStringArg.toArgs(arg[3 + i]);
            }
            this.isCenteredString = centered;
        }

        @Override
        public String toString() {
            return "Str{format='" + this.format + "', args=" + Arrays.toString(this.args) + '}';
        }
    }

    /** {@code DrawTexture}: part of a texture, optionally rotated. */
    public static final class Texture extends HudItem {

        public final String name;
        public final String left;
        public final String top;
        public final String width;
        public final String height;
        public final String uLeft;
        public final String vTop;
        public final String uWidth;
        public final String vHeight;
        public final String rot;

        public Texture(int fileLine, String name, String left, String top, @Nullable String width,
                @Nullable String height, @Nullable String uLeft, @Nullable String vTop, @Nullable String uWidth,
                @Nullable String vHeight, String rot) {
            super(fileLine);
            this.name = name;
            this.left = toFormula(left);
            this.top = toFormula(top);
            this.width = toFormula(width);
            this.height = toFormula(height);
            this.uLeft = toFormula(uLeft);
            this.vTop = toFormula(vTop);
            this.uWidth = toFormula(uWidth);
            this.vHeight = toFormula(vHeight);
            this.rot = toFormula(rot);
        }
    }
}
