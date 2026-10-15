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

// p0 = scanline strength
void main() {
    float k = Control.x;
    vec2 uv = texCoord * 2.0 - 1.0;
    uv *= 1.0 + dot(uv, uv) * 0.08 * k;
    uv = uv * 0.5 + 0.5;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec3 c = texture(InSampler, uv).rgb;
    float scan = 0.5 + 0.5 * sin(uv.y * InSize.y * 3.14159265);
    c *= mix(1.0, 0.75 + 0.25 * scan, Params.x * k);
    float vig = smoothstep(0.9, 0.3, length(texCoord - 0.5));
    c *= mix(1.0, vig, 0.5 * k);
    fragColor = vec4(c, 1.0);
}
