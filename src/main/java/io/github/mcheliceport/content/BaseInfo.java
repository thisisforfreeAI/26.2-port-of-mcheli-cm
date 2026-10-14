package io.github.mcheliceport.content;

import net.minecraft.world.phys.Vec3;

/**
 * Common base of all content definitions.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.MCH_BaseInfo}. The {@code to*} helpers are the
 * value parsers the legacy TXT format uses; they keep upstream's exact behaviour, including the
 * clamping overloads.
 */
public abstract class BaseInfo implements ContentData {

    /** Path of the source file inside its addon pack. */
    public final String filePath;
    public final ContentLocation location;
    /** Content author, from the file's {@code Author} entry; inherited by overrides that leave it out. */
    public String author = "";
    /** Identifier of the parser that produced this definition: {@code txt}, {@code yml} or {@code builtin}. */
    public final String parserIdentifier;

    protected BaseInfo(ContentLocation location, String filePath, String parserIdentifier) {
        this.location = location;
        this.filePath = filePath;
        this.parserIdentifier = parserIdentifier;
    }

    public boolean toBool(String s) {
        return s.equalsIgnoreCase("true");
    }

    public boolean toBool(String s, boolean defaultValue) {
        if (s.equalsIgnoreCase("true")) {
            return true;
        }
        return !s.equalsIgnoreCase("false") && defaultValue;
    }

    public float toFloat(String s) {
        return Float.parseFloat(s);
    }

    public float toFloat(String s, float min, float max) {
        float f = Float.parseFloat(s);
        return f > max ? max : Math.max(f, min);
    }

    public double toDouble(String s) {
        return Double.parseDouble(s);
    }

    public Vec3 toVec3(String x, String y, String z) {
        return new Vec3(this.toDouble(x), this.toDouble(y), this.toDouble(z));
    }

    public int toInt(String s) {
        return Integer.parseInt(s);
    }

    public int toInt(String s, int min, int max) {
        int i = Integer.parseInt(s);
        return i > max ? max : Math.max(i, min);
    }

    /**
     * Parses a hexadecimal colour such as {@code FF00FF00} or {@code 0xFF00FF00}.
     *
     * <p>Upstream meant to accept a leading {@code #} as well, but its check tested for a NUL
     * character instead, so {@code #...} values fail to parse. The port keeps that behaviour so
     * TXT files load exactly as they do upstream.
     */
    public int hex2dec(String s) {
        return !s.startsWith("0x") && !s.startsWith("0X") && s.indexOf(0) != '#'
                ? (int) (Long.decode("0x" + s) & -1L)
                : (int) (Long.decode(s) & -1L);
    }

    public String[] splitParam(String data) {
        return data.split("\\s*,\\s*");
    }

    public String[] splitParamSlash(String data) {
        return data.split("\\s*/\\s*");
    }

    @Override
    public boolean validate() throws Exception {
        return true;
    }

    /** Whether {@code /mcheli reconfig} may change the given TXT key without a restart. */
    public boolean canReloadItem(String item) {
        return false;
    }

    @Override
    public ContentLocation getLocation() {
        return this.location;
    }

    @Override
    public String getContentPath() {
        return this.filePath;
    }

    /** The content name: the lowercased file name without its extension. */
    public String getName() {
        return this.location.path();
    }
}
