#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

// One vehicle part: its model-to-camera-relative transform (the entity pose times the part's
// pose), that transform's normal matrix, and what path A wrote into every vertex.
layout(std140) uniform McheliPart {
    mat4 PartMat;
    mat4 NormalMat;
    vec4 PartColor;
    ivec2 PartLight;
    // Bit 1: light with FlatNormal. Bit 2: use the baked surface shade grey.
    // Bit 4: realistic lighting (sun or moon, point lights and the light probe).
    int Flags;
    // The vehicle's light probe: its box corner relative to the camera (w 1 when there is one),
    // its size, and the block and sky lightmap coordinates of its eight corners, two per vec4.
    vec4 ProbeMin;
    vec4 ProbeSize;
    vec4 ProbeLight[4];
};

// The frame's lighting, shared by every vehicle.
layout(std140) uniform McheliLights {
    // xyz: direction to the sun or moon; w: its strength.
    vec4 KeyDir;
    // rgb: its colour; a: panel sheen strength.
    vec4 KeyColor;
    // rgb: sky fill colour.
    vec4 FillColor;
    // x: number of point lights.
    vec4 PointInfo;
    // xyz: camera-relative position; w: radius in blocks.
    vec4 PointPos[8];
    // rgb: colour times strength.
    vec4 PointColor[8];
};

in vec3 Position;
in vec2 UV0;
// The fourth component holds the vertex's surface shade grey less 128, over 127, or -1 for none.
in vec4 Normal;
// The normal of the triangle's last corner, which upstream's GL_FLAT shading lit it with.
in vec3 FlatNormal;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec2 texCoord0;
out vec3 sheenColor;

vec2 probeCorner(int i) {
    vec4 pair = ProbeLight[i / 2];
    return (i % 2) == 0 ? pair.xy : pair.zw;
}

// Block and sky lightmap coordinates blended across the probe's box.
vec2 probeLight(vec3 position) {
    vec3 t = clamp((position - ProbeMin.xyz) / max(ProbeSize.xyz, vec3(0.001)), 0.0, 1.0);
    vec2 x00 = mix(probeCorner(0), probeCorner(1), t.x);
    vec2 x10 = mix(probeCorner(2), probeCorner(3), t.x);
    vec2 x01 = mix(probeCorner(4), probeCorner(5), t.x);
    vec2 x11 = mix(probeCorner(6), probeCorner(7), t.x);
    return mix(mix(x00, x10, t.y), mix(x01, x11, t.y), t.z);
}

void main() {
    vec4 position = PartMat * vec4(Position, 1.0);
    gl_Position = ProjMat * ModelViewMat * position;

    sphericalVertexDistance = fog_spherical_distance(position.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(position.xyz);

    vec3 normal = normalize(mat3(NormalMat) * ((Flags & 1) != 0 ? FlatNormal : Normal.xyz));
    vec4 color = PartColor;
    if ((Flags & 2) != 0 && Normal.w >= 0.0) {
        color = vec4(vec3((round(Normal.w * 127.0) + 128.0) / 255.0) * PartColor.rgb / (191.0 / 255.0), PartColor.a);
    }
    texCoord0 = UV0;
    sheenColor = vec3(0.0);

    if ((Flags & 4) == 0) {
        vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, color);
        lightMapColor = sample_lightmap(Sampler2, PartLight);
        return;
    }

    vec2 light = ProbeMin.w > 0.0 ? probeLight(position.xyz) : vec2(PartLight);
    vec4 lightmap = texture(Sampler2, clamp(light / 256.0 + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));

    // Under open sky the sun or moon lights the hull from where it really is; under cover the
    // usual two fixed entity lights take over.
    float outdoor = smoothstep(0.35, 0.85, light.y / 240.0);
    float vanilla = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, vec4(1.0)).r;
    float key = max(dot(normal, KeyDir.xyz), 0.0) * KeyDir.w;
    float sky = 0.5 + 0.5 * normal.y;
    vec3 sun = min(FillColor.rgb * (0.42 + 0.2 * sky) + KeyColor.rgb * key * 0.58, vec3(1.0));
    vec3 shade = mix(vec3(vanilla), sun, outdoor);

    // Flashes and fires: a soft wrap so they reach round the hull, fading to nothing at their radius.
    vec3 points = vec3(0.0);
    int count = int(PointInfo.x);
    for (int i = 0; i < 8; i++) {
        if (i >= count) {
            break;
        }
        vec3 toLight = PointPos[i].xyz - position.xyz;
        float distance = length(toLight);
        float radius = PointPos[i].w;
        if (distance < radius) {
            float falloff = 1.0 - distance / radius;
            float facing = distance > 0.001 ? dot(normal, toLight / distance) : 1.0;
            points += PointColor[i].rgb * falloff * sqrt(falloff) * clamp(facing * 0.6 + 0.4, 0.0, 1.0);
        }
    }

    vertexColor = color;
    lightMapColor = vec4(min(shade * lightmap.rgb + points, vec3(1.0)), lightmap.a);

    // A soft sun glint on the panels, only in the open.
    vec3 view = normalize(-position.xyz);
    vec3 halfway = normalize(KeyDir.xyz + view);
    float glint = pow(max(dot(normal, halfway), 0.0), 40.0) * step(0.0, dot(normal, KeyDir.xyz));
    sheenColor = KeyColor.rgb * (glint * KeyDir.w * KeyColor.a * outdoor);
}
