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

// p0 = amplitude, p1 = frequency, p2 = speed
void main() {
    float t = Control.y * Params.z;
    vec2 uv = texCoord;
    float amp = Params.x * Control.x;
    uv += vec2(sin(uv.y * Params.y + t), cos(uv.x * Params.y * 0.8 + t * 1.1)) * amp;
    fragColor = vec4(texture(InSampler, clamp(uv, 0.0, 1.0)).rgb, 1.0);
}
