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
    vec4 texel = texture(Sampler0, texCoord0);
    // Reject nearly transparent atlas matte before overlapping billows can accumulate a rectangle.
    if (texel.a < 0.10) discard;
    // Existing atlas alpha supplies the billow. A stable coarse field opens its edges as it thins.
    // One atlas lookup: no scene copy, depth texture, noise texture, world sampling or light pass.
    vec2 cell = floor(texCoord0 * vec2(textureSize(Sampler0, 0)) * 0.16);
    float grain = fract(52.9829189 * fract(dot(cell, vec2(0.06711056, 0.00583715))));
    float thinning = clamp((0.64 - vertexColor.a) / 0.64, 0.0, 1.0);
    float density = texel.a * (0.55 + 0.45 * grain);
    float coverage = smoothstep(thinning * 0.78, thinning * 0.78 + 0.14, density);
    // Keep hot flame and white-hot centers from the existing explosion sheet intact.
    bool hot = (texel.r > texel.g * 1.05 && texel.g > texel.b * 1.12)
            || min(texel.r, min(texel.g, texel.b)) > 0.92;
    if (hot) coverage = 1.0;
    else texel.rgb = vec3(dot(texel.rgb, vec3(0.299, 0.587, 0.114)));
    vec4 color = vec4(texel.rgb * vertexColor.rgb, texel.a * vertexColor.a * coverage) * ColorModulator;
    if (color.a < 0.0039) discard;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
