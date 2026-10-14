package io.github.mcheliceport.client.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Wavefront OBJ parser. Ported from upstream {@code W_WavefrontObject} with its rules kept:
 * whitespace is collapsed, only {@code g $name} and {@code o $name} lines start a group (other group
 * lines are ignored), faces before any group go to {@code Default}, V is flipped, a group may hold
 * triangles or quads but not both, and lines in another format are errors.
 *
 * <p>Quads are split into two triangles (upstream's VBO path drew quad data as triangles, which
 * scrambled those faces). A vertex without a normal gets its face normal (upstream's VBO path gave
 * it a zero normal).
 */
public final class ObjParser {

    private static final Pattern VERTEX = Pattern.compile("(v( (\\-){0,1}\\d+\\.\\d+){3,4} *\\n)|(v( (\\-){0,1}\\d+\\.\\d+){3,4} *$)");
    private static final Pattern VERTEX_NORMAL = Pattern.compile("(vn( (\\-){0,1}\\d+\\.\\d+){3,4} *\\n)|(vn( (\\-){0,1}\\d+\\.\\d+){3,4} *$)");
    private static final Pattern TEXTURE_COORDINATE = Pattern.compile("(vt( (\\-){0,1}\\d+\\.\\d+){2,3} *\\n)|(vt( (\\-){0,1}\\d+\\.\\d+){2,3} *$)");
    private static final Pattern FACE_V_VT_VN = Pattern.compile("(f( \\d+/\\d+/\\d+){3,4} *\\n)|(f( \\d+/\\d+/\\d+){3,4} *$)");
    private static final Pattern FACE_V_VT = Pattern.compile("(f( \\d+/\\d+){3,4} *\\n)|(f( \\d+/\\d+){3,4} *$)");
    private static final Pattern FACE_V_VN = Pattern.compile("(f( \\d+//\\d+){3,4} *\\n)|(f( \\d+//\\d+){3,4} *$)");
    private static final Pattern FACE_V = Pattern.compile("(f( \\d+){3,4} *\\n)|(f( \\d+){3,4} *$)");
    private static final Pattern GROUP = Pattern.compile("([go]( [-\\$\\w\\d]+) *\\n)|([go]( [-\\$\\w\\d]+) *$)");

    private final String fileName;
    private final McheliModel.Builder bounds = new McheliModel.Builder();
    private final McheliModel.FloatList vertices = new McheliModel.FloatList();
    private final McheliModel.FloatList normals = new McheliModel.FloatList();
    private final McheliModel.FloatList uvs = new McheliModel.FloatList();
    private final List<McheliModel.Group> groups = new ArrayList<>();
    private String groupName;
    private McheliModel.FloatList groupData;
    /** -1 none yet, 3 triangles, 4 quads (upstream's glDrawingMode 4 and 7). */
    private int groupFaceSize;

    private ObjParser(String fileName) {
        this.fileName = fileName;
    }

    public static McheliModel parse(String fileName, InputStream in) throws ModelFormatException {
        return new ObjParser(fileName).load(in);
    }

    private McheliModel load(InputStream in) throws ModelFormatException {
        int lineCount = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                line = normalizeWhitespace(line);
                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }
                switch (line.charAt(0)) {
                    case 'v' -> {
                        if (line.length() > 1) {
                            switch (line.charAt(1)) {
                                case ' ' -> vertex(line, lineCount);
                                case 'n' -> normal(line, lineCount);
                                case 't' -> textureCoordinate(line, lineCount);
                                default -> {
                                }
                            }
                        }
                    }
                    case 'f' -> {
                        if (this.groupData == null) {
                            startGroup("Default");
                        }
                        face(line, lineCount);
                    }
                    case 'g', 'o' -> {
                        if (line.length() > 2 && line.charAt(2) == '$') {
                            if (!GROUP.matcher(line).matches()) {
                                throw badLine(line, lineCount);
                            }
                            String name = line.substring(line.indexOf(' ') + 1);
                            if (!name.isEmpty()) {
                                finishGroup();
                                startGroup(name);
                            }
                        }
                    }
                    default -> {
                    }
                }
            }
            finishGroup();
        } catch (IOException e) {
            throw new ModelFormatException("IO Exception reading model format", e);
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            throw new ModelFormatException("Error parsing line " + lineCount + " in file '" + this.fileName + "'", e);
        }
        return this.bounds.build(this.fileName, this.groups);
    }

    private ModelFormatException badLine(String line, int lineCount) {
        return new ModelFormatException("Error parsing entry ('" + line + "', line " + lineCount + ") in file '"
                + this.fileName + "' - Incorrect format");
    }

    private void startGroup(String name) {
        this.groupName = name;
        this.groupData = new McheliModel.FloatList();
        this.groupFaceSize = -1;
    }

    private void finishGroup() {
        if (this.groupData != null) {
            this.groups.add(new McheliModel.Group(this.groupName, this.groupData.toArray()));
        }
    }

    private static String[] values(String line) {
        return line.substring(line.indexOf(' ') + 1).split(" ");
    }

    private void vertex(String line, int lineCount) throws ModelFormatException {
        if (!VERTEX.matcher(line).matches()) {
            throw badLine(line, lineCount);
        }
        String[] t = values(line);
        // Upstream returned no vertex for four values (x y z w), so later indices shift.
        if (t.length == 3) {
            float x = Float.parseFloat(t[0]);
            float y = Float.parseFloat(t[1]);
            float z = Float.parseFloat(t[2]);
            this.bounds.checkMinMax(x, y, z);
            this.vertices.add(x);
            this.vertices.add(y);
            this.vertices.add(z);
        }
    }

    private void normal(String line, int lineCount) throws ModelFormatException {
        if (!VERTEX_NORMAL.matcher(line).matches()) {
            throw badLine(line, lineCount);
        }
        String[] t = values(line);
        if (t.length == 3) {
            this.normals.add(Float.parseFloat(t[0]));
            this.normals.add(Float.parseFloat(t[1]));
            this.normals.add(Float.parseFloat(t[2]));
        }
    }

    private void textureCoordinate(String line, int lineCount) throws ModelFormatException {
        if (!TEXTURE_COORDINATE.matcher(line).matches()) {
            throw badLine(line, lineCount);
        }
        String[] t = values(line);
        this.uvs.add(Float.parseFloat(t[0]));
        this.uvs.add(1.0F - Float.parseFloat(t[1]));
    }

    private void face(String line, int lineCount) throws ModelFormatException {
        boolean vtvn = FACE_V_VT_VN.matcher(line).matches();
        boolean vt = !vtvn && FACE_V_VT.matcher(line).matches();
        boolean vn = !vtvn && !vt && FACE_V_VN.matcher(line).matches();
        if (!vtvn && !vt && !vn && !FACE_V.matcher(line).matches()) {
            throw badLine(line, lineCount);
        }
        String[] tokens = values(line);
        int n = tokens.length;
        if (this.groupFaceSize == -1) {
            this.groupFaceSize = n;
        } else if (this.groupFaceSize != n) {
            throw new ModelFormatException("Error parsing entry ('" + line + "', line " + lineCount + ") in file '"
                    + this.fileName + "' - Invalid number of points for face (expected " + this.groupFaceSize
                    + ", found " + n + ")");
        }
        int[] v = new int[n];
        int[] t = new int[n];
        int[] nn = new int[n];
        for (int i = 0; i < n; i++) {
            String[] sub = tokens[i].split(vn ? "//" : "/");
            v[i] = Integer.parseInt(sub[0]) - 1;
            t[i] = vtvn || vt ? Integer.parseInt(sub[1]) - 1 : -1;
            nn[i] = vtvn ? Integer.parseInt(sub[2]) - 1 : vn ? Integer.parseInt(sub[1]) - 1 : -1;
            // Upstream looked the entries up while parsing; a bad index is an error there too.
            checkIndex(v[i], this.vertices.size / 3);
            if (t[i] >= 0) {
                checkIndex(t[i], this.uvs.size / 2);
            }
            if (nn[i] >= 0) {
                checkIndex(nn[i], this.normals.size / 3);
            }
        }
        float[] p = this.vertices.data;
        float[] fn = McheliModel.faceNormal(p[v[0] * 3], p[v[0] * 3 + 1], p[v[0] * 3 + 2], p[v[1] * 3],
                p[v[1] * 3 + 1], p[v[1] * 3 + 2], p[v[2] * 3], p[v[2] * 3 + 1], p[v[2] * 3 + 2]);
        int[] order = n == 4 ? new int[] {0, 1, 2, 0, 2, 3} : new int[] {0, 1, 2};
        for (int i : order) {
            McheliModel.FloatList out = this.groupData;
            out.add(p[v[i] * 3]);
            out.add(p[v[i] * 3 + 1]);
            out.add(p[v[i] * 3 + 2]);
            if (t[i] >= 0) {
                out.add(this.uvs.data[t[i] * 2]);
                out.add(this.uvs.data[t[i] * 2 + 1]);
            } else {
                out.add(0.0F);
                out.add(0.0F);
            }
            if (nn[i] >= 0) {
                out.add(this.normals.data[nn[i] * 3]);
                out.add(this.normals.data[nn[i] * 3 + 1]);
                out.add(this.normals.data[nn[i] * 3 + 2]);
            } else {
                out.add(fn[0]);
                out.add(fn[1]);
                out.add(fn[2]);
            }
        }
    }

    private static void checkIndex(int index, int size) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + size);
        }
    }

    /** Upstream {@code W_ModelCustom#normalizeWhitespace}: runs of spaces and tabs become one space, trimmed. */
    static String normalizeWhitespace(String line) {
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
        int start = 0;
        int end = sb.length();
        while (start < end && sb.charAt(start) == ' ') {
            start++;
        }
        while (end > start && sb.charAt(end - 1) == ' ') {
            end--;
        }
        return sb.substring(start, end);
    }
}
