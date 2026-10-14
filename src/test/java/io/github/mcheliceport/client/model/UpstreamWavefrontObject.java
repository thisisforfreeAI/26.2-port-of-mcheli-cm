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
 * Test oracle: upstream's {@code W_WavefrontObject} parsing code, copied with the GL drawing code
 * removed (the reader decodes ISO-8859-1, as the port does).
 */
@SuppressWarnings("all")
class UpstreamWavefrontObject extends UpstreamModelCustom {

    public ArrayList<W_Vertex> vertices = new ArrayList<>();
    public ArrayList<W_Vertex> vertexNormals = new ArrayList<>();
    public ArrayList<W_TextureCoordinate> textureCoordinates = new ArrayList<>();
    public ArrayList<GroupObject> groupObjects = new ArrayList<>();
    private Pattern vertexPattern = Pattern.compile("(v( (\\-){0,1}\\d+\\.\\d+){3,4} *\\n)|(v( (\\-){0,1}\\d+\\.\\d+){3,4} *$)");
    private Pattern vertexNormalPattern = Pattern.compile("(vn( (\\-){0,1}\\d+\\.\\d+){3,4} *\\n)|(vn( (\\-){0,1}\\d+\\.\\d+){3,4} *$)");
    private Pattern textureCoordinatePattern = Pattern.compile("(vt( (\\-){0,1}\\d+\\.\\d+){2,3} *\\n)|(vt( (\\-){0,1}\\d+\\.\\d+){2,3} *$)");
    private Pattern face_V_VT_VN_Pattern = Pattern.compile("(f( \\d+/\\d+/\\d+){3,4} *\\n)|(f( \\d+/\\d+/\\d+){3,4} *$)");
    private Pattern face_V_VT_Pattern = Pattern.compile("(f( \\d+/\\d+){3,4} *\\n)|(f( \\d+/\\d+){3,4} *$)");
    private Pattern face_V_VN_Pattern = Pattern.compile("(f( \\d+//\\d+){3,4} *\\n)|(f( \\d+//\\d+){3,4} *$)");
    private Pattern face_V_Pattern = Pattern.compile("(f( \\d+){3,4} *\\n)|(f( \\d+){3,4} *$)");
    private Pattern groupObjectPattern = Pattern.compile("([go]( [-\\$\\w\\d]+) *\\n)|([go]( [-\\$\\w\\d]+) *$)");
    private Matcher vertexMatcher;
    private Matcher vertexNormalMatcher;
    private Matcher textureCoordinateMatcher;
    private Matcher face_V_VT_VN_Matcher;
    private Matcher face_V_VT_Matcher;
    private Matcher face_V_VN_Matcher;
    private Matcher face_V_Matcher;
    private Matcher groupObjectMatcher;
    private GroupObject currentGroupObject;

    private String fileName;

    UpstreamWavefrontObject(String filename, InputStream inputStream) throws ModelFormatException {
        this.fileName = filename;
        loadObjModel(inputStream);
    }

    private boolean isValidVertexLine(String line) {
        if (vertexMatcher != null) vertexMatcher.reset();
        vertexMatcher = vertexPattern.matcher(line);
        return vertexMatcher.matches();
    }

    private boolean isValidVertexNormalLine(String line) {
        if (vertexNormalMatcher != null) vertexNormalMatcher.reset();
        vertexNormalMatcher = vertexNormalPattern.matcher(line);
        return vertexNormalMatcher.matches();
    }

    private boolean isValidTextureCoordinateLine(String line) {
        if (textureCoordinateMatcher != null) textureCoordinateMatcher.reset();
        textureCoordinateMatcher = textureCoordinatePattern.matcher(line);
        return textureCoordinateMatcher.matches();
    }

    private boolean isValidFace_V_VT_VN_Line(String line) {
        if (face_V_VT_VN_Matcher != null) face_V_VT_VN_Matcher.reset();
        face_V_VT_VN_Matcher = face_V_VT_VN_Pattern.matcher(line);
        return face_V_VT_VN_Matcher.matches();
    }

    private boolean isValidFace_V_VT_Line(String line) {
        if (face_V_VT_Matcher != null) face_V_VT_Matcher.reset();
        face_V_VT_Matcher = face_V_VT_Pattern.matcher(line);
        return face_V_VT_Matcher.matches();
    }

    private boolean isValidFace_V_VN_Line(String line) {
        if (face_V_VN_Matcher != null) face_V_VN_Matcher.reset();
        face_V_VN_Matcher = face_V_VN_Pattern.matcher(line);
        return face_V_VN_Matcher.matches();
    }

    private boolean isValidFace_V_Line(String line) {
        if (face_V_Matcher != null) face_V_Matcher.reset();
        face_V_Matcher = face_V_Pattern.matcher(line);
        return face_V_Matcher.matches();
    }

    private boolean isValidFaceLine(String line) {
        return (isValidFace_V_VT_VN_Line(line) || isValidFace_V_VT_Line(line) || isValidFace_V_VN_Line(line) || isValidFace_V_Line(line));
    }

    private boolean isValidGroupObjectLine(String line) {
        if (groupObjectMatcher != null) groupObjectMatcher.reset();
        groupObjectMatcher = groupObjectPattern.matcher(line);
        return groupObjectMatcher.matches();
    }

    private void loadObjModel(InputStream inputStream) throws ModelFormatException {
        BufferedReader reader = null;
        String currentLine;
        int lineCount = 0;

        try {
            reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.ISO_8859_1));

            while ((currentLine = reader.readLine()) != null) {
                lineCount++;
                currentLine = normalizeWhitespace(currentLine);
                if (currentLine.startsWith("#") || currentLine.isEmpty()) continue;

                char c0 = currentLine.charAt(0);

                switch (c0) {
                    case 'v':
                        if (currentLine.length() > 1) {
                            char c1 = currentLine.charAt(1);
                            switch (c1) {
                                case ' ' -> {
                                    // vertex
                                    W_Vertex vertex = parseVertex(currentLine, lineCount);
                                    if (vertex != null) {
                                        checkMinMax(vertex);
                                        vertices.add(vertex);
                                    }
                                }
                                case 'n' -> {
                                    // vertex normal
                                    W_Vertex normal = parseVertexNormal(currentLine, lineCount);
                                    if (normal != null) vertexNormals.add(normal);
                                }
                                case 't' -> {
                                    // texture coordinate
                                    W_TextureCoordinate tex = parseTextureCoordinate(currentLine, lineCount);
                                    if (tex != null) textureCoordinates.add(tex);
                                }
                            }
                        }
                        break;

                    case 'f':
                        if (currentGroupObject == null) currentGroupObject = new GroupObject("Default");
                        W_Face face = parseFace(currentLine, lineCount);
                        currentGroupObject.faces.add(face);
                        break;

                    case 'g':
                        // group
                        if (currentLine.length() > 2 && currentLine.charAt(2) == '$') {
                            GroupObject group = parseGroupObject(currentLine, lineCount);
                            if (group != null && currentGroupObject != null) groupObjects.add(currentGroupObject);
                            currentGroupObject = group;
                        }
                        break;

                    case 'o':
                        if (currentLine.length() > 2 && currentLine.charAt(2) == '$') {
                            GroupObject group2 = parseGroupObject(currentLine, lineCount);
                            if (group2 != null && currentGroupObject != null) groupObjects.add(currentGroupObject);
                            currentGroupObject = group2;
                        }
                        break;

                    default:
                        // ignore unknown lines
                        break;
                }
            }

            this.groupObjects.add(this.currentGroupObject);
        } catch (IOException var16) {
            throw new ModelFormatException("IO Exception reading model format", var16);
        } finally {
            this.checkMinMaxFinal();

            try {
                reader.close();
            } catch (IOException var15) {
            }

            try {
                inputStream.close();
            } catch (IOException var14) {
            }
        }
    }

    private W_Vertex parseVertex(String line, int lineCount) throws ModelFormatException {
        W_Vertex vertex = null;
        if (isValidVertexLine(line)) {
            line = line.substring(line.indexOf(" ") + 1);
            String[] tokens = line.split(" ");
            try {
                if (tokens.length == 2) return new W_Vertex(Float.parseFloat(tokens[0]), Float.parseFloat(tokens[1]));
                if (tokens.length == 3)
                    return new W_Vertex(Float.parseFloat(tokens[0]), Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]));
            } catch (NumberFormatException e) {
                throw new ModelFormatException(String.format("Number formatting error at line %d", new Object[]{Integer.valueOf(lineCount)}), e);
            }
        } else {
            throw new ModelFormatException("Error parsing entry ('" + line + "', line " + lineCount + ") in file '" + this.fileName + "' - Incorrect format");
        }
        return vertex;
    }

    private W_Vertex parseVertexNormal(String line, int lineCount) throws ModelFormatException {
        W_Vertex vertexNormal = null;
        if (isValidVertexNormalLine(line)) {
            line = line.substring(line.indexOf(" ") + 1);
            String[] tokens = line.split(" ");
            try {
                if (tokens.length == 3)
                    return new W_Vertex(Float.parseFloat(tokens[0]), Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]));
            } catch (NumberFormatException e) {
                throw new ModelFormatException(String.format("Number formatting error at line %d", new Object[]{Integer.valueOf(lineCount)}), e);
            }
        } else {
            throw new ModelFormatException("Error parsing entry ('" + line + "', line " + lineCount + ") in file '" + this.fileName + "' - Incorrect format");
        }
        return vertexNormal;
    }

    private W_TextureCoordinate parseTextureCoordinate(String line, int lineCount) throws ModelFormatException {
        W_TextureCoordinate textureCoordinate = null;
        if (isValidTextureCoordinateLine(line)) {
            line = line.substring(line.indexOf(" ") + 1);
            String[] tokens = line.split(" ");
            try {
                if (tokens.length == 2)
                    return new W_TextureCoordinate(Float.parseFloat(tokens[0]), 1.0F - Float.parseFloat(tokens[1]));
                if (tokens.length == 3)
                    return new W_TextureCoordinate(Float.parseFloat(tokens[0]), 1.0F - Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]));
            } catch (NumberFormatException e) {
                throw new ModelFormatException(String.format("Number formatting error at line %d", new Object[]{Integer.valueOf(lineCount)}), e);
            }
        } else {
            throw new ModelFormatException("Error parsing entry ('" + line + "', line " + lineCount + ") in file '" + this.fileName + "' - Incorrect format");
        }
        return textureCoordinate;
    }

    private W_Face parseFace(String line, int lineCount) throws ModelFormatException {
        W_Face face = null;
        if (isValidFaceLine(line)) {
            face = new W_Face();
            String trimmedLine = line.substring(line.indexOf(" ") + 1);
            String[] tokens = trimmedLine.split(" ");
            String[] subTokens = null;
            if (tokens.length == 3) {
                if (this.currentGroupObject.glDrawingMode == -1) {
                    this.currentGroupObject.glDrawingMode = 4;
                } else if (this.currentGroupObject.glDrawingMode != 4) {
                    throw new ModelFormatException("Error parsing entry ('" + line + "', line " + lineCount + ") in file '" + this.fileName + "' - Invalid number of points for face (expected 4, found " + tokens.length + ")");
                }
            } else if (tokens.length == 4) {
                if (this.currentGroupObject.glDrawingMode == -1) {
                    this.currentGroupObject.glDrawingMode = 7;
                } else if (this.currentGroupObject.glDrawingMode != 7) {
                    throw new ModelFormatException("Error parsing entry ('" + line + "', line " + lineCount + ") in file '" + this.fileName + "' - Invalid number of points for face (expected 3, found " + tokens.length + ")");
                }
            }
            if (isValidFace_V_VT_VN_Line(line)) {
                face.vertices = new W_Vertex[tokens.length];
                face.textureCoordinates = new W_TextureCoordinate[tokens.length];
                face.vertexNormals = new W_Vertex[tokens.length];
                for (int i = 0; i < tokens.length; i++) {
                    subTokens = tokens[i].split("/");
                    face.vertices[i] = this.vertices.get(Integer.parseInt(subTokens[0]) - 1);
                    face.textureCoordinates[i] = this.textureCoordinates.get(Integer.parseInt(subTokens[1]) - 1);
                    face.vertexNormals[i] = this.vertexNormals.get(Integer.parseInt(subTokens[2]) - 1);
                }
                face.faceNormal = face.calculateFaceNormal();
            } else if (isValidFace_V_VT_Line(line)) {
                face.vertices = new W_Vertex[tokens.length];
                face.textureCoordinates = new W_TextureCoordinate[tokens.length];
                for (int i = 0; i < tokens.length; i++) {
                    subTokens = tokens[i].split("/");
                    face.vertices[i] = this.vertices.get(Integer.parseInt(subTokens[0]) - 1);
                    face.textureCoordinates[i] = this.textureCoordinates.get(Integer.parseInt(subTokens[1]) - 1);
                }
                face.faceNormal = face.calculateFaceNormal();
            } else if (isValidFace_V_VN_Line(line)) {
                face.vertices = new W_Vertex[tokens.length];
                face.vertexNormals = new W_Vertex[tokens.length];
                for (int i = 0; i < tokens.length; i++) {
                    subTokens = tokens[i].split("//");
                    face.vertices[i] = this.vertices.get(Integer.parseInt(subTokens[0]) - 1);
                    face.vertexNormals[i] = this.vertexNormals.get(Integer.parseInt(subTokens[1]) - 1);
                }
                face.faceNormal = face.calculateFaceNormal();
            } else if (isValidFace_V_Line(line)) {
                face.vertices = new W_Vertex[tokens.length];
                for (int i = 0; i < tokens.length; i++)
                    face.vertices[i] = this.vertices.get(Integer.parseInt(tokens[i]) - 1);
                face.faceNormal = face.calculateFaceNormal();
            } else {
                throw new ModelFormatException("Error parsing entry ('" + line + "', line " + lineCount + ") in file '" + this.fileName + "' - Incorrect format");
            }
        } else {
            throw new ModelFormatException("Error parsing entry ('" + line + "', line " + lineCount + ") in file '" + this.fileName + "' - Incorrect format");
        }
        return face;
    }

    private GroupObject parseGroupObject(String line, int lineCount) throws ModelFormatException {
        GroupObject group = null;
        if (isValidGroupObjectLine(line)) {
            String trimmedLine = line.substring(line.indexOf(" ") + 1);
            if (trimmedLine.length() > 0) group = new GroupObject(trimmedLine);
        } else {
            throw new ModelFormatException("Error parsing entry ('" + line + "', line " + lineCount + ") in file '" + this.fileName + "' - Incorrect format");
        }
        return group;
    }
}
