#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    // Reuse the wisp silhouette, without its soot-colored albedo. Vertex RGB already carries light.
    float density = texture(Sampler0, texCoord0).a;
    if (density < 0.10) discard;
    vec4 color = vec4(vertexColor.rgb, smoothstep(0.10, 0.85, density) * vertexColor.a) * ColorModulator;
    if (color.a < 0.0039) discard;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
