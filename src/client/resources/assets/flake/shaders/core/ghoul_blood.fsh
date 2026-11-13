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

    float kind = vertexColor.r;
    float seed = vertexColor.g;
    float pulse = vertexColor.b;
    float alpha = vertexColor.a;

    float t = GameTime * 420.0;

    vec3 darkCrimson = vec3(0.22, 0.008, 0.025);
    vec3 bloodRed = vec3(0.66, 0.025, 0.065);
    vec3 rubyGlow = vec3(1.00, 0.14, 0.18);
    vec3 hotCore = vec3(1.00, 0.45, 0.36);

    vec3 rgb;
    float outAlpha;

    if (kind >= 0.90) {
        // Soft atmospheric mist / floor aura (smooth radial SDF)
        vec2 p = texCoord0 * 2.0 - 1.0;
        float r2 = dot(p, p);
        if (r2 >= 1.0) discard;
        float r = sqrt(r2);
        float angle = atan(p.y, p.x);
        float swirl = 0.65 + 0.35 * sin(angle * 3.0 - t * 0.45 + r * 4.0 + seed * 6.28318);
        float falloff = exp(-r2 * 3.6) * (1.0 - smoothstep(0.65, 1.0, r));
        rgb = mix(darkCrimson, bloodRed, falloff) + rubyGlow * (falloff * swirl * (0.45 + 0.35 * pulse));
        outAlpha = alpha * falloff * swirl;
    } else if (kind >= 0.66) {
        // Smooth SDF blood droplet / orb (no polygon edges)
        vec2 p = texCoord0 * 2.0 - 1.0;
        // Slight teardrop pinch toward the top
        p.x *= 1.0 + max(0.0, p.y) * 0.35;
        float r2 = dot(p, p);
        if (r2 >= 1.0) discard;
        float r = sqrt(r2);
        float body = 1.0 - smoothstep(0.45, 0.98, r);
        float core = exp(-r2 * 6.0);
        float halo = exp(-r2 * 2.2) * (1.0 - r);
        rgb = mix(darkCrimson, bloodRed, body);
        rgb = mix(rgb, rubyGlow, core * 0.85);
        rgb += hotCore * (core * core * (0.45 + 0.4 * pulse));
        outAlpha = alpha * max(body, halo * 0.55);
    } else {
        // Smooth flowing liquid blood ribbon
        float wobble = sin(u * 22.0 - t * 1.15 + seed * 6.28318) * 0.085
                     + sin(u * 41.0 + t * 0.85 - seed * 12.566) * 0.04;
        float across = abs((v - 0.5) * 2.0 + wobble);
        if (across >= 1.0) discard;

        float vein = 0.5 + 0.5 * sin(u * 16.0 - t * (1.0 + kind * 0.6) + seed * 6.28318 + across * 2.5);
        float filament = exp(-across * across * 7.5);
        float body = 1.0 - smoothstep(0.28, 0.98, across);
        float softHalo = exp(-across * across * 2.4) * (1.0 - across);

        // Smoothly taper alpha near root/tip (u = 0 and u = 1)
        float endFade = smoothstep(0.0, 0.08, u) * (1.0 - smoothstep(0.88, 1.0, u));

        rgb = mix(darkCrimson, bloodRed, body);
        rgb = mix(rgb, rubyGlow, clamp(filament * (0.65 + 0.35 * vein), 0.0, 1.0));
        rgb += hotCore * (filament * filament * (0.32 + 0.45 * pulse + 0.25 * kind));
        outAlpha = alpha * endFade * max(body * (0.78 + 0.22 * vein), softHalo * 0.45);
    }

    if (outAlpha <= 0.01) discard;

    vec4 color = vec4(rgb, clamp(outAlpha, 0.0, 0.95)) * ColorModulator;
    float fragmentDistance = 1.0 / gl_FragCoord.w;
    fragColor = apply_fog(color, fragmentDistance, fragmentDistance,
                          FogEnvironmentalStart, FogEnvironmentalEnd,
                          FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
