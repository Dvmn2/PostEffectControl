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

// p0 = pixel size at full intensity
void main() {
    float size = max(1.0, mix(1.0, Params.x, Control.x));
    vec2 cell = vec2(size) / InSize;
    vec2 uv = (floor(texCoord / cell) + 0.5) * cell;
    fragColor = vec4(texture(InSampler, uv).rgb, 1.0);
}
