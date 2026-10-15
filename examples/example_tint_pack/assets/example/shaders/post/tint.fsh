#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform EffectParams {
    vec4 Control;   // x = intensity 0..1, y = time (s), w = seed
    vec4 Color;     // rgb = color
    vec4 Params;    // p0..p3
    vec4 Params2;   // p4..p7
};

in vec2 texCoord;
out vec4 fragColor;

// p0 = strength (see "strength" in posteffectcontrol/tint.json)
void main() {
    vec3 src = texture(InSampler, texCoord).rgb;
    vec3 tinted = mix(src, src * Color.rgb * 1.6, Params.x * Control.x);
    fragColor = vec4(tinted, 1.0);
}
