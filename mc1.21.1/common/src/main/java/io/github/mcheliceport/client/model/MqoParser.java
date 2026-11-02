package io.github.mcheliceport.client.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Metasequoia MQO parser. Ported from upstream {@code W_MetasequoiaObject} with its rules kept:
 * coordinates are divided by 100, faces are read in reverse vertex order, a quad becomes two
 * triangles, vertex normals are smoothed over adjacent faces within the object's {@code facet}
 * angle when the object has {@code shading 1}, and objects are then merged as upstream's
 * {@code ModelVBO#cleanUpMsqMess}: an object whose name does not start with {@code $} joins the
 * {@code $} object before it.
 *
 * <p>Object names are read as ISO-8859-1, since stock files name objects in Shift JIS; only the
 * ASCII {@code $part} names matter.
 */
public final class MqoParser {

    private final String fileName;
    private final McheliModel.Builder bounds = new McheliModel.Builder();

    /** One triangle of the object being read. */
    private record Face(int[] ids, float[] uv, float[] normal) {
    }

    private MqoParser(String fileName) {
        this.fileName = fileName;
    }

    public static McheliModel parse(String fileName, InputStream in) throws ModelFormatException {
        return new MqoParser(fileName).load(in);
    }

    private static boolean isObjectLine(String[] s) {
        return s.length >= 2 && s[0].equals("Object") && s[1].length() >= 4 && s[1].charAt(0) == '"';
    }

    private McheliModel load(InputStream in) throws ModelFormatException {
        List<McheliModel.Group> objects = new ArrayList<>();
        int lineCount = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                line = ObjParser.normalizeWhitespace(line);
                String[] head = line.split(" ");
                if (!isObjectLine(head)) {
                    continue;
                }
                String name = head[1].substring(1, head[1].length() - 1);
                if (name.isEmpty()) {
                    continue;
                }
                int vertexNum = 0;
                double facet = Math.cos(0.785398163375);
                boolean shading = false;
                while ((line = reader.readLine()) != null) {
                    lineCount++;
                    line = ObjParser.normalizeWhitespace(line);
                    if (line.equalsIgnoreCase("shading 1")) {
                        shading = true;
                    }
                    String[] s = line.split(" ");
                    if (s.length == 2 && s[0].equalsIgnoreCase("facet")) {
                        facet = Math.cos(Double.parseDouble(s[1]) * 3.1415926535 / 180.0);
                    }
                    if (s[0].equals("vertex")) {
                        vertexNum = Integer.parseInt(s[1]);
                        break;
                    }
                }
                List<Face> faces = new ArrayList<>();
                McheliModel.FloatList vertices = new McheliModel.FloatList();
                if (vertexNum > 0) {
                    while ((line = reader.readLine()) != null) {
                        lineCount++;
                        line = ObjParser.normalizeWhitespace(line);
                        String[] s = line.split(" ");
                        if (s.length == 3) {
                            float x = Float.parseFloat(s[0]) / 100.0F;
                            float y = Float.parseFloat(s[1]) / 100.0F;
                            float z = Float.parseFloat(s[2]) / 100.0F;
                            this.bounds.checkMinMax(x, y, z);
                            vertices.add(x);
                            vertices.add(y);
                            vertices.add(z);
                            if (--vertexNum <= 0) {
                                break;
                            }
                        } else {
                            throw new ModelFormatException("format error : " + this.fileName + " : line=" + lineCount);
                        }
                    }
                    int faceNum = 0;
                    while ((line = reader.readLine()) != null) {
                        lineCount++;
                        line = ObjParser.normalizeWhitespace(line);
                        String[] s = line.split(" ");
                        if (s[0].equals("face")) {
                            faceNum = Integer.parseInt(s[1]);
                            break;
                        }
                    }
                    if (faceNum > 0) {
                        while ((line = reader.readLine()) != null) {
                            lineCount++;
                            line = ObjParser.normalizeWhitespace(line);
                            String[] s = line.split(" ");
                            if (s.length > 2) {
                                if (Integer.parseInt(s[0]) >= 3) {
                                    parseFace(line, vertices.data, faces);
                                }
                                if (--faceNum <= 0) {
                                    break;
                                }
                            }
                        }
                    }
                }
                objects.add(new McheliModel.Group(name, triangles(faces, vertices.data, shading, facet)));
            }
        } catch (IOException e) {
            throw new ModelFormatException("IO Exception reading model format : " + this.fileName, e);
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            throw new ModelFormatException("format error : " + this.fileName + " : line=" + lineCount, e);
        }
        return this.bounds.build(this.fileName, merge(objects));
    }

    /** Upstream {@code W_MetasequoiaObject#parseFace}: a triangle or a quad as two triangles. */
    private static void parseFace(String line, float[] v, List<Face> faces) {
        String[] s = line.split("[ VU)(M]+");
        int vnum = Integer.parseInt(s[0]);
        if (vnum == 3) {
            int[] ids = {Integer.parseInt(s[3]), Integer.parseInt(s[2]), Integer.parseInt(s[1])};
            float[] uv = s.length >= 11 ? uv(s, 9, 7, 5) : new float[6];
            faces.add(new Face(ids, uv, normal(v, ids)));
        } else if (vnum == 4) {
            int[] ids1 = {Integer.parseInt(s[3]), Integer.parseInt(s[2]), Integer.parseInt(s[1])};
            float[] uv1 = s.length >= 12 ? uv(s, 10, 8, 6) : new float[6];
            Face f1 = new Face(ids1, uv1, normal(v, ids1));
            int[] ids2 = {Integer.parseInt(s[4]), Integer.parseInt(s[3]), Integer.parseInt(s[1])};
            float[] uv2 = s.length >= 14 ? uv(s, 12, 10, 6) : new float[6];
            Face f2 = new Face(ids2, uv2, normal(v, ids2));
            faces.add(f1);
            faces.add(f2);
        }
    }

    private static float[] uv(String[] s, int a, int b, int c) {
        return new float[] {Float.parseFloat(s[a]), Float.parseFloat(s[a + 1]), Float.parseFloat(s[b]),
                Float.parseFloat(s[b + 1]), Float.parseFloat(s[c]), Float.parseFloat(s[c + 1])};
    }

    private static float[] normal(float[] v, int[] ids) {
        int a = ids[0] * 3;
        int b = ids[1] * 3;
        int c = ids[2] * 3;
        if (c + 2 >= v.length || b + 2 >= v.length || a + 2 >= v.length || a < 0 || b < 0 || c < 0) {
            throw new IndexOutOfBoundsException("vertex id out of range");
        }
        return McheliModel.faceNormal(v[a], v[a + 1], v[a + 2], v[b], v[b + 1], v[b + 2], v[c], v[c + 1], v[c + 2]);
    }

    /**
     * Upstream {@code calcVerticesNormal} and {@code getVerticesNormalFromFace}, with each vertex's
     * faces looked up in a table instead of a scan of every face; the sums run in the same order,
     * so the floats are the same.
     */
    private static float[] triangles(List<Face> faces, float[] v, boolean shading, double facet) {
        float facetF = (float) facet;
        int vertexCount = 0;
        for (Face f : faces) {
            for (int id : f.ids) {
                vertexCount = Math.max(vertexCount, id + 1);
            }
        }
        // Faces of each vertex in face order; a face that names a vertex twice counts once.
        int[] counts = new int[vertexCount];
        for (Face f : faces) {
            for (int i = 0; i < 3; i++) {
                if (!seenBefore(f.ids, i)) {
                    counts[f.ids[i]]++;
                }
            }
        }
        int[][] byVertex = new int[vertexCount][];
        for (int i = 0; i < vertexCount; i++) {
            byVertex[i] = new int[counts[i]];
            counts[i] = 0;
        }
        for (int fi = 0; fi < faces.size(); fi++) {
            int[] ids = faces.get(fi).ids;
            for (int i = 0; i < 3; i++) {
                if (!seenBefore(ids, i)) {
                    byVertex[ids[i]][counts[ids[i]]++] = fi;
                }
            }
        }
        float[] out = new float[faces.size() * 3 * McheliModel.STRIDE];
        int o = 0;
        for (Face f : faces) {
            float[] fn = f.normal;
            for (int i = 0; i < 3; i++) {
                int id = f.ids[i];
                // getVerticesNormalFromFace
                float x = 0.0F;
                float y = 0.0F;
                float z = 0.0F;
                for (int other : byVertex[id]) {
                    float[] n = faces.get(other).normal;
                    if (!(n[0] * fn[0] + n[1] * fn[1] + n[2] * fn[2] < facetF)) {
                        x = x + n[0];
                        y = y + n[1];
                        z = z + n[2];
                    }
                }
                double d = Math.sqrt(x * x + y * y + z * z);
                x = (float) (x / d);
                y = (float) (y / d);
                z = (float) (z / d);
                // W_Vertex#normalize again, as calcVerticesNormal did
                d = Math.sqrt(x * x + y * y + z * z);
                x = (float) (x / d);
                y = (float) (y / d);
                z = (float) (z / d);
                boolean smooth = shading && fn[0] * x + fn[1] * y + fn[2] * z >= facet;
                out[o++] = v[id * 3];
                out[o++] = v[id * 3 + 1];
                out[o++] = v[id * 3 + 2];
                out[o++] = f.uv[i * 2];
                out[o++] = f.uv[i * 2 + 1];
                out[o++] = smooth ? x : fn[0];
                out[o++] = smooth ? y : fn[1];
                out[o++] = smooth ? z : fn[2];
            }
        }
        return out;
    }

    private static boolean seenBefore(int[] ids, int i) {
        for (int j = 0; j < i; j++) {
            if (ids[j] == ids[i]) {
                return true;
            }
        }
        return false;
    }

    /** Upstream {@code ModelVBO#cleanUpMsqMess}. */
    static List<McheliModel.Group> merge(List<McheliModel.Group> objects) {
        List<McheliModel.Group> result = new ArrayList<>();
        String keyName = null;
        McheliModel.FloatList keyData = null;
        for (McheliModel.Group obj : objects) {
            if (obj.name().charAt(0) == '$') {
                if (keyName != null) {
                    result.set(result.size() - 1, new McheliModel.Group(keyName, keyData.toArray()));
                }
                keyName = obj.name();
                keyData = new McheliModel.FloatList();
                for (float f : obj.data()) {
                    keyData.add(f);
                }
                result.add(obj);
            } else if (keyName != null) {
                for (float f : obj.data()) {
                    keyData.add(f);
                }
            } else {
                result.add(obj);
            }
        }
        if (keyName != null) {
            result.set(result.size() - 1, new McheliModel.Group(keyName, keyData.toArray()));
        }
        return result;
    }
}
