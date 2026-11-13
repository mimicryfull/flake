#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    float u = texCoord0.x;
    float v = texCoord0.y;
    float across = abs(v - 0.5) * 2.0;

    float kind = vertexColor.r;
    float seed = vertexColor.g;
    float pulse = vertexColor.b;
    float alpha = vertexColor.a;

    float t = GameTime * 420.0;
    float veinWave = 0.5 + 0.5 * sin(u * 18.0 - t * (1.0 + kind * 0.5) + seed * 6.28318 + sin(v * 6.28318) * 1.3);
    float fineRipple = 0.5 + 0.5 * sin(u * 34.0 + t * 1.6 - seed * 12.566);

    float coreMask = smoothstep(0.62, 0.0, across);
    float bodyMask = smoothstep(1.0, 0.18, across);
    float edgeHighlight = smoothstep(0.92, 0.58, across) * smoothstep(0.25, 0.60, across);

    vec3 darkCrimson = vec3(0.26, 0.01, 0.03);
    vec3 bloodRed = vec3(0.68, 0.03, 0.07);
    vec3 rubyGlow = vec3(1.00, 0.16, 0.20);
    vec3 hotCore = vec3(1.00, 0.42, 0.34);

    vec3 rgb = mix(darkCrimson, bloodRed, bodyMask);
    rgb = mix(rgb, rubyGlow, clamp(coreMask * (0.55 + 0.45 * veinWave) + edgeHighlight * 0.35 * fineRipple, 0.0, 1.0));
    rgb += hotCore * (coreMask * coreMask * (0.28 + 0.45 * pulse + 0.25 * kind));

    float outAlpha = clamp(alpha * bodyMask * (0.72 + 0.28 * veinWave), 0.0, 0.96);
    if (outAlpha <= 0.01) discard;

    vec4 color = vec4(rgb, outAlpha) * ColorModulator;
    float fragmentDistance = 1.0 / gl_FragCoord.w;
    fragColor = apply_fog(color, fragmentDistance, fragmentDistance,
                          FogEnvironmentalStart, FogEnvironmentalEnd,
                          FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
