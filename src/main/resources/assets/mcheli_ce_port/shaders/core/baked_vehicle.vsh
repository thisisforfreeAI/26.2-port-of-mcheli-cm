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
    int Flags;
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

void main() {
    vec4 position = PartMat * vec4(Position, 1.0);
    gl_Position = ProjMat * ModelViewMat * position;

    sphericalVertexDistance = fog_spherical_distance(position.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(position.xyz);

    vec3 normal = normalize(mat3(NormalMat) * ((Flags & 1) != 0 ? FlatNormal : Normal.xyz));
    vec4 color = PartColor;
    if ((Flags & 2) != 0 && Normal.w >= 0.0) {
        color = vec4(vec3((round(Normal.w * 127.0) + 128.0) / 255.0), PartColor.a);
    }
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, color);
    lightMapColor = sample_lightmap(Sampler2, PartLight);
    texCoord0 = UV0;
}
