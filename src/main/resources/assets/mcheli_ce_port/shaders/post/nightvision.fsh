#version 330

// Upstream's shaders/program/nightvision.fsh (by TV90), in vanilla's post-effect style:
// yellowish pixels are brightened a little.

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 c = texture(InSampler, texCoord);
    float yellowness = (c.r + c.g) * 0.5 - c.b;
    const float threshold = 0.2;
    float isYellow = step(threshold, yellowness);
    vec3 enhanced = clamp(c.rgb + vec3(0.1, 0.1, -0.05), 0.0, 1.0);
    vec3 result = mix(c.rgb, enhanced, isYellow);
    fragColor = vec4(result, c.a);
}
