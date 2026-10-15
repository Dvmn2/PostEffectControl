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

// p0 = beats per minute; color from command
void main() {
    float k = Control.x;
    vec3 c = texture(InSampler, texCoord).rgb;
    float ph = fract(Control.y * Params.x / 60.0);
    float pulse = pow(max(sin(ph * 6.2831853), 0.0), 4.0);
    float d = length((texCoord - 0.5) * vec2(InSize.x / InSize.y, 1.0));
    float glow = smoothstep(0.25, 0.9, d) * (0.35 + 0.65 * pulse) * k;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(c, vec3(l), 0.25 * k * pulse);
    fragColor = vec4(mix(c, Color.rgb, clamp(glow * 0.8, 0.0, 1.0)), 1.0);
}
