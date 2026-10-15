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

// p0 = frost coverage, p1 = blur, p2 = cold tint
void main() {
    float k = Control.x;
    vec2 uv = texCoord;
    vec2 px = 1.0 / InSize;
    float aspect = InSize.x / InSize.y;

    vec3 col = texture(InSampler, uv).rgb;

    float blurAmt = Params.y * k;
    if (blurAmt > 0.001) {
        vec3 acc = vec3(0.0);
        float wsum = 0.0;
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                float wt = 1.0 / (1.0 + float(x * x + y * y));
                acc += texture(InSampler, uv + vec2(x, y) * px * (1.5 + 3.0 * blurAmt)).rgb * wt;
                wsum += wt;
            }
        }
        col = mix(col, acc / wsum, clamp(blurAmt * 1.5, 0.0, 1.0));
    }

    vec2 c = (uv - 0.5) * vec2(aspect, 1.0);
    float n = fbm(uv * 6.0 + Control.w);
    float edge = length(c) * 1.15 + (n - 0.5) * 0.35;
    float mask = smoothstep(0.55 - 0.35 * Params.x * k, 0.95, edge);
    mask = clamp(mask * k * 1.3, 0.0, 1.0);

    float crystals = noise(uv * vec2(aspect, 1.0) * 90.0);
    vec3 frost = mix(Color.rgb, vec3(1.0), 0.55 + 0.4 * crystals);

    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 cold = mix(col, vec3(lum) * Color.rgb * 1.25, Params.z * k);
    col = mix(cold, frost, mask * 0.85);

    fragColor = vec4(col, 1.0);
}
