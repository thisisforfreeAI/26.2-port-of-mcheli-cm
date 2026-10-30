#version 330

// Vanilla's core/entity.fsh as the vehicle pipelines use it, plus the sun's sheen on the panels,
// added after lighting and scaled by the texture so dark rubber and paint glint less than metal.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;
in vec3 sheenColor;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (texel.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    vec4 color = texel * vertexColor * ColorModulator;
    color *= lightMapColor;
    color.rgb += sheenColor * mix(0.35, 1.0, dot(texel.rgb, vec3(0.299, 0.587, 0.114)));

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
