#version 330

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
layout(std140) uniform HeatConfig {
    vec4 Screen; // width, height, seconds, capsule count
    vec4 Capsules[16]; // two vec4s per capsule: uv/depth/radius, uv/depth/strength
};
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 p = texCoord * Screen.xy;
    float sceneDepth = texture(DepthSampler, texCoord).r;
    vec2 offset = vec2(0.0);
    float nearestHeat = 0.0;
    for (int i = 0; i < 8; ++i) {
        if (float(i) >= Screen.w) break;
        vec4 a = Capsules[i * 2];
        vec4 b = Capsules[i * 2 + 1];
        vec2 start = a.xy * Screen.xy;
        vec2 line = (b.xy - a.xy) * Screen.xy;
        float along = dot(line, line) < 4.0 ? 0.35 : clamp(dot(p - start, line) / dot(line, line), 0.0, 1.0);
        float depth = mix(a.z, b.z, along);
        if (sceneDepth > depth + 0.00001) continue; // reversed depth: nearer solid surfaces hide heat
        float radius = a.w * mix(0.6, 1.3, along);
        float edge = length(p - start - line * along) / max(1.0, radius);
        if (edge >= 1.0) continue;
        float envelope = (1.0 - smoothstep(0.2, 1.0, edge))
                * smoothstep(0.0, 0.12, along) * (1.0 - smoothstep(0.55, 1.0, along));
        vec2 waves = vec2(sin(p.y * 0.28 - Screen.z * 13.0 + float(i) * 2.1),
                          sin(p.x * 0.21 + p.y * 0.13 - Screen.z * 10.0));
        offset += waves * envelope * b.w * 1.5;
        nearestHeat = max(nearestHeat, depth);
    }
    offset = clamp(offset, vec2(-2.0), vec2(2.0));
    vec2 uv = clamp(texCoord + offset / Screen.xy, vec2(0.0), vec2(1.0));
    if (texture(DepthSampler, uv).r > nearestHeat + 0.00001) uv = texCoord;
    fragColor = texture(SceneSampler, uv);
}
