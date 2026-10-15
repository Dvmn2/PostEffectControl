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

// p0 = channel offset strength
void main() {
    vec2 d = (texCoord - 0.5) * Params.x * Control.x;
    float r = texture(InSampler, texCoord + d).r;
    float g = texture(InSampler, texCoord).g;
    float b = texture(InSampler, texCoord - d).b;
    fragColor = vec4(r, g, b, 1.0);
}
