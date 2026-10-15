#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform EffectParams {
    vec4 Control;   // x = intensity 0..1, y = time (s), w = seed
    vec4 Color;
    vec4 Params;    // p0..p3
    vec4 Params2;   // p4..p7
};

in vec2 texCoord;
out vec4 fragColor;

// p0 = inner radius, p1 = softness; color from command
void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    vec2 d = (texCoord - 0.5) * vec2(InSize.x / InSize.y, 1.0);
    float v = smoothstep(Params.x, Params.x + max(Params.y, 0.01), length(d)) * Control.x;
    fragColor = vec4(mix(c, Color.rgb, clamp(v, 0.0, 1.0)), 1.0);
}
