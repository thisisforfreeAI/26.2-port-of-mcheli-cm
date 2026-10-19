#version 330

// Upstream's shaders/program/thermal.fsh (by TV90), in vanilla's post-effect style: bright,
// grey pixels (the white-drawn mobs and vehicles) turn white; the sky is dimmed by half and
// everything else to a quarter of its grey.

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 c = texture(InSampler, texCoord);
    float brightness = (c.r + c.g + c.b) / 3.0;
    float saturation = max(max(c.r, c.g), c.b) - min(min(c.r, c.g), c.b);
    float blueDominance = c.b / max(0.001, max(c.r, c.g));
    bool skyLike = blueDominance > 1.1 && brightness > 0.5;
    if (saturation < 0.5 && brightness > 0.5 && !skyLike) {
        float whiteLevel = clamp((brightness - 0.5) / 0.65, 0.0, 1.0);
        whiteLevel = pow(whiteLevel, 0.5) * 1.2;
        fragColor = vec4(vec3(min(whiteLevel, 1.0)), c.a);
    } else {
        float gray = dot(c.rgb, vec3(0.299, 0.587, 0.114));
        gray *= skyLike ? 0.5 : 0.25;
        fragColor = vec4(vec3(gray), c.a);
    }
}
