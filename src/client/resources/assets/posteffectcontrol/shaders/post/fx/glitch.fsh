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

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p *= 2.03;
        a *= 0.5;
    }
    return v;
}

// FLASHING effect. p0 = strength
void main() {
    float k = Control.x;
    float t = floor(Control.y * 12.0);
    vec2 uv = texCoord;
    float band = floor(uv.y * (8.0 + 30.0 * hash(vec2(t, 1.0))));
    float active = step(1.0 - 0.35 * k, hash(vec2(band, t)));
    float shift = (hash(vec2(band, t + 3.0)) - 0.5) * 0.12 * k * Params.x * active;
    float r = texture(InSampler, vec2(clamp(uv.x + shift * 1.5, 0.0, 1.0), uv.y)).r;
    float g = texture(InSampler, vec2(clamp(uv.x + shift, 0.0, 1.0), uv.y)).g;
    float b = texture(InSampler, vec2(clamp(uv.x + shift * 0.5, 0.0, 1.0), uv.y)).b;
    fragColor = vec4(r, g, b, 1.0);
}
