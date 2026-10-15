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

// p0 = radius in pixels
void main() {
    float r = Params.x * Control.x;
    vec3 acc = texture(InSampler, texCoord).rgb;
    float total = 1.0;
    const int N = 32;
    for (int i = 0; i < N; i++) {
        float fi = float(i);
        float rad = sqrt((fi + 0.5) / float(N));
        float ang = fi * 2.39996323;
        vec2 o = vec2(cos(ang), sin(ang)) * rad * r / InSize;
        acc += texture(InSampler, texCoord + o).rgb;
        total += 1.0;
    }
    fragColor = vec4(acc / total, 1.0);
}
