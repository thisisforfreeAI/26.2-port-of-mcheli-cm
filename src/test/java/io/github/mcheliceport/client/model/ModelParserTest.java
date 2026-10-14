package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.mcheliceport.client.model.UpstreamModelCustom.GroupObject;
import io.github.mcheliceport.client.model.UpstreamModelCustom.W_Face;
import io.github.mcheliceport.client.model.UpstreamModelCustom.W_TextureCoordinate;
import io.github.mcheliceport.client.model.UpstreamModelCustom.W_Vertex;
import io.github.mcheliceport.content.ContentTestSupport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Exit criterion E2 of docs/M4_PLAN.md: the port's OBJ and MQO parsers give upstream's triangles
 * for every stock model. The oracle is upstream's parsing code ({@link UpstreamWavefrontObject},
 * {@link UpstreamMetasequoiaObject}) plus the vertex layout of upstream's {@code ModelVBO}, with
 * the two deliberate differences of {@link ObjParser} applied to the oracle's output: quads are
 * split into two triangles, and a vertex without a normal gets its face normal.
 */
class ModelParserTest {

    private static List<Path> stockModels() throws IOException {
        Path root = ContentTestSupport.projectDir().resolve("src/main/resources/assets/mcheli/models");
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".obj") || p.toString().endsWith(".mqo")).sorted()
                    .toList();
        }
    }

    @Test
    void everyStockModelMatchesUpstream() throws Exception {
        List<Path> models = stockModels();
        assertTrue(models.size() > 150, "expected the stock models, found " + models.size());
        List<String> failures = new ArrayList<>();
        for (Path file : models) {
            byte[] bytes = Files.readAllBytes(file);
            String name = file.getFileName().toString();
            String problem = compare(name, bytes);
            if (problem != null) {
                failures.add(file + ": " + problem);
            }
            try {
                parse(name, new ByteArrayInputStream(bytes));
            } catch (ModelFormatException e) {
                failures.add(file + ": stock model does not parse: " + e);
            }
        }
        if (!failures.isEmpty()) {
            fail(failures.size() + " models differ:\n" + String.join("\n", failures));
        }
    }

    /** Null when the port and the oracle agree. */
    private static String compare(String name, byte[] bytes) {
        boolean obj = name.endsWith(".obj");
        UpstreamModelCustom upstream;
        List<McheliModel.Group> expected;
        try {
            if (obj) {
                UpstreamWavefrontObject o = new UpstreamWavefrontObject(name, new ByteArrayInputStream(bytes));
                upstream = o;
                expected = groups(o.groupObjects, true);
            } else {
                UpstreamMetasequoiaObject m = new UpstreamMetasequoiaObject(name, new ByteArrayInputStream(bytes));
                upstream = m;
                expected = groups(cleanUpMsqMess(m.groupObjects), false);
            }
        } catch (Exception e) {
            try {
                parse(name, new ByteArrayInputStream(bytes));
                return "upstream fails (" + e + ") but the port parses it";
            } catch (ModelFormatException expectedFailure) {
                return null;
            }
        }
        McheliModel actual;
        try {
            actual = parse(name, new ByteArrayInputStream(bytes));
        } catch (ModelFormatException e) {
            return "the port fails: " + e;
        }
        float[] ub = {upstream.minX, upstream.minY, upstream.minZ, upstream.maxX, upstream.maxY, upstream.maxZ};
        float[] pb = {actual.minX, actual.minY, actual.minZ, actual.maxX, actual.maxY, actual.maxZ};
        if (!sameFloats(ub, pb)) {
            return "bounds differ: " + java.util.Arrays.toString(ub) + " vs " + java.util.Arrays.toString(pb);
        }
        if (expected.size() != actual.groups.size()) {
            return "group count " + expected.size() + " vs " + actual.groups.size();
        }
        for (int i = 0; i < expected.size(); i++) {
            McheliModel.Group e = expected.get(i);
            McheliModel.Group a = actual.groups.get(i);
            if (!e.name().equals(a.name())) {
                return "group " + i + " named " + e.name() + " vs " + a.name();
            }
            if (!sameFloats(e.data(), a.data())) {
                return "group " + e.name() + " data differs (" + e.data().length + " vs " + a.data().length + " floats)";
            }
        }
        return null;
    }

    private static McheliModel parse(String name, InputStream in) throws ModelFormatException {
        return name.endsWith(".obj") ? ObjParser.parse(name, in) : MqoParser.parse(name, in);
    }

    private static boolean sameFloats(float[] a, float[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (Float.floatToIntBits(a[i]) != Float.floatToIntBits(b[i])) {
                return false;
            }
        }
        return true;
    }

    /** Upstream {@code ModelVBO#cleanUpMsqMess}. */
    private static List<GroupObject> cleanUpMsqMess(List<GroupObject> groupObjects) {
        List<GroupObject> result = new ArrayList<>();
        GroupObject currentKey = null;
        for (GroupObject obj : groupObjects) {
            String name = obj.name;
            if (name.isEmpty()) {
                continue;
            }
            if (name.charAt(0) == '$') {
                currentKey = new GroupObject(name);
                currentKey.faces.addAll(obj.faces);
                result.add(currentKey);
            } else if (currentKey != null) {
                currentKey.faces.addAll(obj.faces);
            } else {
                result.add(obj);
            }
        }
        return result;
    }

    /**
     * Upstream {@code ModelVBO#prepareGroupData} without the unused W coordinate, with quads split
     * and face normals for vertices without one (OBJ only).
     */
    private static List<McheliModel.Group> groups(List<GroupObject> groupObjects, boolean obj) {
        List<McheliModel.Group> out = new ArrayList<>();
        for (GroupObject g : groupObjects) {
            if (g == null) {
                continue;
            }
            List<Float> data = new ArrayList<>();
            for (W_Face face : g.faces) {
                int[] order = face.vertices.length == 4 ? new int[] {0, 1, 2, 0, 2, 3} : new int[] {0, 1, 2};
                for (int i : order) {
                    W_Vertex vert = face.vertices[i];
                    W_TextureCoordinate tex = face.textureCoordinates != null && face.textureCoordinates.length > 0
                            ? face.textureCoordinates[i] : new W_TextureCoordinate(0, 0);
                    W_Vertex normal = face.vertexNormals != null ? face.vertexNormals[i]
                            : obj ? face.faceNormal : new W_Vertex(0, 0, 0);
                    data.add(vert.x);
                    data.add(vert.y);
                    data.add(vert.z);
                    data.add(tex.u);
                    data.add(tex.v);
                    data.add(normal.x);
                    data.add(normal.y);
                    data.add(normal.z);
                }
            }
            float[] array = new float[data.size()];
            for (int i = 0; i < array.length; i++) {
                array[i] = data.get(i);
            }
            out.add(new McheliModel.Group(g.name, array));
        }
        return out;
    }

    @Test
    void handWrittenObj() throws Exception {
        String obj = """
                # comment
                v 0.0 0.0 0.0
                v 1.0 0.0 0.0
                v 1.0 1.0 0.0
                v 0.0 1.0 0.0
                vt 0.0 0.0
                vt 1.0 0.25
                vt 1.0 1.0
                f 1/1 2/2 3/3
                g ignored
                f 1 3 4
                g $rotor
                f 1/1 2/2 3/3 4/1
                o $empty
                """;
        McheliModel m = ObjParser.parse("t.obj", stream(obj));
        assertEquals(List.of("Default", "$rotor", "$empty"), m.groups.stream().map(McheliModel.Group::name).toList());
        assertEquals(6, m.groups.get(0).vertexCount());
        assertEquals(6, m.groups.get(1).vertexCount(), "a quad becomes two triangles");
        assertEquals(0, m.groups.get(2).vertexCount());
        assertTrue(m.containsPart("$ROTOR"));
        assertEquals(0.75F, m.groups.get(0).data()[McheliModel.STRIDE + 4], "V is flipped");
        assertEquals(1.0F, m.groups.get(0).data()[7], "face normal for a vertex without one");
        assertEquals(m.groups.subList(0, 3), m.body(), "no $body: the whole model");
        assertThrows(ModelFormatException.class,
                () -> ObjParser.parse("bad.obj", stream("v 0.0 0.0 0.0\nv 1 0 0\n")));
        assertThrows(ModelFormatException.class, () -> ObjParser.parse("mixed.obj",
                stream("v 0.0 0.0 0.0\nv 1.0 0.0 0.0\nv 1.0 1.0 0.0\nv 0.0 1.0 0.0\nf 1 2 3\nf 1 2 3 4\n")));
    }

    @Test
    void handWrittenMqo() throws Exception {
        String mqo = """
                Metasequoia Document
                Object "loose" {
                	vertex 3 {
                		0 0 0
                		100 0 0
                		0 100 0
                	}
                	face 1 {
                		3 V(0 1 2) M(0) UV(0 0 1 0 0 1)
                	}
                }
                Object "$body" {
                	shading 1
                	facet 59.5
                	vertex 4 {
                		0 0 0
                		100 0 0
                		100 100 0
                		0 100 0
                	}
                	face 1 {
                		4 V(0 1 2 3) M(0) UV(0 0 1 0 1 1 0 1)
                	}
                }
                Object "detail" {
                	vertex 3 {
                		0 0 0
                		100 0 0
                		0 100 0
                	}
                	face 1 {
                		3 V(0 1 2)
                	}
                }
                """;
        McheliModel m = MqoParser.parse("t.mqo", stream(mqo));
        assertEquals(List.of("loose", "$body"), m.groups.stream().map(McheliModel.Group::name).toList());
        assertEquals(9, m.groups.get(1).vertexCount(), "the quad and the merged detail object");
        assertEquals(1.0F, m.maxX, "coordinates are divided by 100");
        assertEquals(List.of(m.groups.get(1)), m.body());
        assertEquals(null, compare("t.mqo", mqo.getBytes(StandardCharsets.ISO_8859_1)));
    }

    private static InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
    }
}
