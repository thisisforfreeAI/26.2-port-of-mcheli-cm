package io.github.mcheliceport.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A parsed OBJ or MQO model: named groups of triangles, with no GPU resources. The renderer
 * submits the triangles every frame (render path A, PORTING_PLAN.md section 5.4).
 *
 * <p>Ported from upstream's {@code W_ModelCustom} and the group layout of {@code ModelVBO}: an MQO
 * model's groups are already merged as {@code ModelVBO#cleanUpMsqMess} did, so a {@code $part}
 * group holds the faces of the unnamed objects after it.
 */
public final class McheliModel {

    /** Floats per vertex in {@link Group#data}: x, y, z, u, v, nx, ny, nz. */
    public static final int STRIDE = 8;

    /** One group: {@link #vertexCount()} vertices, three per triangle. */
    public record Group(String name, float[] data) {
        public int vertexCount() {
            return this.data.length / STRIDE;
        }
    }

    public final String name;
    public final List<Group> groups;
    public final float minX;
    public final float minY;
    public final float minZ;
    public final float maxX;
    public final float maxY;
    public final float maxZ;

    public McheliModel(String name, List<Group> groups, float minX, float minY, float minZ, float maxX, float maxY,
            float maxZ) {
        this.name = name;
        this.groups = List.copyOf(groups);
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    /** Whether a group has this name, ignoring case (upstream {@code containsPart}). */
    public boolean containsPart(String partName) {
        for (Group g : this.groups) {
            if (g.name.equalsIgnoreCase(partName)) {
                return true;
            }
        }
        return false;
    }

    /** The groups with this name, ignoring case (upstream {@code renderPart}). */
    public List<Group> part(String partName) {
        List<Group> out = new ArrayList<>(1);
        for (Group g : this.groups) {
            if (g.name.equalsIgnoreCase(partName)) {
                out.add(g);
            }
        }
        return out;
    }

    /** The groups upstream's {@code renderBody} draws: {@code $body}, or the whole model without one. */
    public List<Group> body() {
        return containsPart("$body") ? part("$body") : this.groups;
    }

    public int vertexCount() {
        int n = 0;
        for (Group g : this.groups) {
            n += g.vertexCount();
        }
        return n;
    }

    /** {@code helicopters/ah-64} style key in lower case. */
    public static String key(String dir, String name) {
        return (dir + "/" + name).toLowerCase(Locale.ROOT);
    }

    /** Approximate memory use in bytes, for the debug log. */
    public long byteSize() {
        return 4L * STRIDE * vertexCount();
    }

    @Override
    public String toString() {
        return "McheliModel[" + this.name + ", " + this.groups.size() + " groups, " + vertexCount() / 3
                + " triangles]";
    }

    /** Collects triangles and bounds while parsing. */
    static final class Builder {
        float minX = 100000.0F;
        float minY = 100000.0F;
        float minZ = 100000.0F;
        float maxX = -100000.0F;
        float maxY = -100000.0F;
        float maxZ = -100000.0F;

        /** Upstream {@code W_ModelCustom#checkMinMax}. */
        void checkMinMax(float x, float y, float z) {
            if (x < this.minX) {
                this.minX = x;
            }
            if (y < this.minY) {
                this.minY = y;
            }
            if (z < this.minZ) {
                this.minZ = z;
            }
            if (x > this.maxX) {
                this.maxX = x;
            }
            if (y > this.maxY) {
                this.maxY = y;
            }
            if (z > this.maxZ) {
                this.maxZ = z;
            }
        }

        McheliModel build(String name, List<Group> groups) {
            return new McheliModel(name, groups, this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
        }
    }

    /** A growable float array. */
    static final class FloatList {
        float[] data = new float[96];
        int size;

        void add(float f) {
            if (this.size == this.data.length) {
                this.data = java.util.Arrays.copyOf(this.data, this.size * 2);
            }
            this.data[this.size++] = f;
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(this.data, this.size);
        }
    }

    /** Upstream {@code W_Face#calculateFaceNormal}: (v1 - v0) x (v2 - v0), normalised as 1.12's Vec3d. */
    static float[] faceNormal(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2,
            float z2) {
        double ax = x1 - x0;
        double ay = y1 - y0;
        double az = z1 - z0;
        double bx = x2 - x0;
        double by = y2 - y0;
        double bz = z2 - z0;
        double cx = ay * bz - az * by;
        double cy = az * bx - ax * bz;
        double cz = ax * by - ay * bx;
        // 1.12's MathHelper.sqrt returned a float.
        double len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len < 1.0E-4) {
            return new float[] {0.0F, 0.0F, 0.0F};
        }
        return new float[] {(float) (cx / len), (float) (cy / len), (float) (cz / len)};
    }
}
