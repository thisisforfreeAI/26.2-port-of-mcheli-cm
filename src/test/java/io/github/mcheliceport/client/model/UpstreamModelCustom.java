package io.github.mcheliceport.client.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test oracle: upstream's {@code W_ModelCustom}, {@code W_Vertex}, {@code W_TextureCoordinate},
 * {@code W_Face}, {@code GroupObject} and the CPU side of {@code ModelVBO}, with GL code removed.
 */
@SuppressWarnings("all")
class UpstreamModelCustom {

    public float min = 100000.0F;
    public float minX = 100000.0F;
    public float minY = 100000.0F;
    public float minZ = 100000.0F;
    public float max = -100000.0F;
    public float maxX = -100000.0F;
    public float maxY = -100000.0F;
    public float maxZ = -100000.0F;
    public float size = 0.0F;
    public float sizeX = 0.0F;
    public float sizeY = 0.0F;
    public float sizeZ = 0.0F;

    public void checkMinMax(W_Vertex v) {
        if (v.x < this.minX) {
            this.minX = v.x;
        }

        if (v.y < this.minY) {
            this.minY = v.y;
        }

        if (v.z < this.minZ) {
            this.minZ = v.z;
        }

        if (v.x > this.maxX) {
            this.maxX = v.x;
        }

        if (v.y > this.maxY) {
            this.maxY = v.y;
        }

        if (v.z > this.maxZ) {
            this.maxZ = v.z;
        }
    }

    public void checkMinMaxFinal() {
        if (this.minX < this.min) {
            this.min = this.minX;
        }

        if (this.minY < this.min) {
            this.min = this.minY;
        }

        if (this.minZ < this.min) {
            this.min = this.minZ;
        }

        if (this.maxX > this.max) {
            this.max = this.maxX;
        }

        if (this.maxY > this.max) {
            this.max = this.maxY;
        }

        if (this.maxZ > this.max) {
            this.max = this.maxZ;
        }

        this.sizeX = this.maxX - this.minX;
        this.sizeY = this.maxY - this.minY;
        this.sizeZ = this.maxZ - this.minZ;
        this.size = this.max - this.min;
    }






    public static String normalizeWhitespace(String line) {
        StringBuilder sb = new StringBuilder(line.length());
        boolean lastWasWhitespace = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ' || c == '\t') {
                if (!lastWasWhitespace) {
                    sb.append(' ');
                    lastWasWhitespace = true;
                }
            } else {
                sb.append(c);
                lastWasWhitespace = false;
            }
        }

        int start = 0, end = sb.length();
        while (start < end && sb.charAt(start) == ' ') start++;
        while (end > start && sb.charAt(end - 1) == ' ') end--;

        return sb.substring(start, end);
    }


    static class W_Vertex {
    public float x;
    public float y;
    public float z;

    public W_Vertex(float x, float y) {
        this(x, y, 0.0F);
    }

    public W_Vertex(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void normalize() {
        double d = Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        this.x = (float) (this.x / d);
        this.y = (float) (this.y / d);
        this.z = (float) (this.z / d);
    }

    public void add(W_Vertex v) {
        this.x = this.x + v.x;
        this.y = this.y + v.y;
        this.z = this.z + v.z;
    }

    public boolean equal(W_Vertex v) {
        return this.x == v.x && this.y == v.y && this.z == v.z;
    }
    }

    static class W_TextureCoordinate {
    public final float u;
    public final float v;
    public final float w;

    public W_TextureCoordinate(float u, float v) {
        this(u, v, 0.0F);
    }

    public W_TextureCoordinate(float u, float v, float w) {
        this.u = u;
        this.v = v;
        this.w = w;
    }
    }

    /** Minecraft 1.12's {@code Vec3d}, the parts {@code calculateFaceNormal} uses. */
    record Vec3d(double x, double y, double z) {
        static final Vec3d ZERO = new Vec3d(0, 0, 0);

        Vec3d crossProduct(Vec3d vec) {
            return new Vec3d(this.y * vec.z - this.z * vec.y, this.z * vec.x - this.x * vec.z,
                    this.x * vec.y - this.y * vec.x);
        }

        Vec3d normalize() {
            double d0 = (float) Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
            return d0 < 1.0E-4D ? ZERO : new Vec3d(this.x / d0, this.y / d0, this.z / d0);
        }
    }

    static class W_Face {
        public int[] verticesID;
        public W_Vertex[] vertices;
        public W_Vertex[] vertexNormals;
        public W_Vertex faceNormal;
        public W_TextureCoordinate[] textureCoordinates;

    public W_Vertex calculateFaceNormal() {
        Vec3d v1 = new Vec3d(this.vertices[1].x - this.vertices[0].x, this.vertices[1].y - this.vertices[0].y,
                this.vertices[1].z - this.vertices[0].z);
        Vec3d v2 = new Vec3d(this.vertices[2].x - this.vertices[0].x, this.vertices[2].y - this.vertices[0].y,
                this.vertices[2].z - this.vertices[0].z);
        Vec3d normalVector = v1.crossProduct(v2).normalize();
        return new W_Vertex((float) normalVector.x, (float) normalVector.y, (float) normalVector.z);
    }
    }

    static class GroupObject {
        public final String name;
        public final ArrayList<W_Face> faces = new ArrayList<>();
        public int glDrawingMode;

        public GroupObject(String name) {
            this(name, -1);
        }

        public GroupObject(String name, int glDrawingMode) {
            this.name = name;
            this.glDrawingMode = glDrawingMode;
        }
    }
}
