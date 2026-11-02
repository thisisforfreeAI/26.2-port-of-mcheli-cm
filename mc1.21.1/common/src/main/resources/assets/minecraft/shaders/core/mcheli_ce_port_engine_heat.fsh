#version 150

// Local exhaust refraction (26.2's post/engine_heat), for 1.21.1's GL depth (0 near, 1 far).
uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
uniform vec4 Screen; // width, height, seconds, capsule count
uniform vec4 C0;
uniform vec4 C1;
uniform vec4 C2;
uniform vec4 C3;
uniform vec4 C4;
uniform vec4 C5;
uniform vec4 C6;
uniform vec4 C7;
uniform vec4 C8;
uniform vec4 C9;
uniform vec4 C10;
uniform vec4 C11;
uniform vec4 C12;
uniform vec4 C13;
uniform vec4 C14;
uniform vec4 C15;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 Capsules[16] = vec4[16](C0, C1, C2, C3, C4, C5, C6, C7, C8, C9, C10, C11, C12, C13, C14, C15);
    vec2 p = texCoord * Screen.xy;
    float sceneDepth = texture(DepthSampler, texCoord).r;
    vec2 offset = vec2(0.0);
    float nearestHeat = 1.0;
    for (int i = 0; i < 8; ++i) {
        if (float(i) >= Screen.w) break;
        vec4 a = Capsules[i * 2];
        vec4 b = Capsules[i * 2 + 1];
        vec2 start = a.xy * Screen.xy;
        vec2 line = (b.xy - a.xy) * Screen.xy;
        float along = dot(line, line) < 4.0 ? 0.35 : clamp(dot(p - start, line) / dot(line, line), 0.0, 1.0);
        float depth = mix(a.z, b.z, along);
        if (sceneDepth < depth - 0.00001) continue; // nearer solid surfaces hide heat
        float radius = a.w * mix(0.6, 1.3, along);
        float edge = length(p - start - line * along) / max(1.0, radius);
        if (edge >= 1.0) continue;
        float envelope = (1.0 - smoothstep(0.2, 1.0, edge))
                * smoothstep(0.0, 0.12, along) * (1.0 - smoothstep(0.55, 1.0, along));
        vec2 waves = vec2(sin(p.y * 0.28 - Screen.z * 13.0 + float(i) * 2.1),
                          sin(p.x * 0.21 + p.y * 0.13 - Screen.z * 10.0));
        offset += waves * envelope * b.w * 1.5;
        nearestHeat = min(nearestHeat, depth);
    }
    offset = clamp(offset, vec2(-2.0), vec2(2.0));
    vec2 uv = clamp(texCoord + offset / Screen.xy, vec2(0.0), vec2(1.0));
    if (texture(DepthSampler, uv).r < nearestHeat - 0.00001) uv = texCoord;
    fragColor = texture(SceneSampler, uv);
}
