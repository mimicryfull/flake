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
    float kind = vertexColor.r;
    float seed = vertexColor.g;
    float pulse = vertexColor.b;
    float alpha = vertexColor.a;

    float t = GameTime * 420.0;

    vec3 abyssCrimson = vec3(0.08, 0.002, 0.008);
    vec3 deepBlood    = vec3(0.36, 0.008, 0.025);
    vec3 richCrimson  = vec3(0.68, 0.020, 0.050);
    vec3 rubyEdge     = vec3(0.92, 0.070, 0.105);

    vec3 rgb;
    float outAlpha;

    if (kind >= 0.90) {
        // Soft fog-like crimson glow enveloping the voxels
        vec2 p = texCoord0 * 2.0 - 1.0;
        float r2 = dot(p, p);
        if (r2 >= 1.0) discard;
        float r = sqrt(r2);
        float angle = atan(p.y, p.x);
        float mist = 0.78 + 0.22 * sin(angle * 2.0 - t * 0.22 + r * 3.2 + seed * 6.28318);
        float falloff = exp(-r2 * 2.7) * (1.0 - smoothstep(0.55, 1.0, r));
        rgb = mix(abyssCrimson, deepBlood, falloff * 0.85) + richCrimson * (falloff * mist * (0.45 + 0.12 * pulse));
        outAlpha = alpha * falloff * mist;
    } else {
        // Deep, dark blood voxel face with subtle inner depth & soft ruby rim
        vec2 d = abs(texCoord0 - 0.5) * 2.0;
        float boxDist = max(d.x, d.y);
        float radial = length(d);

        float shimmer = 0.5 + 0.5 * sin(t * 0.45 + seed * 12.56637);
        float core = exp(-radial * radial * 2.4);
        float rim = smoothstep(0.62, 0.96, boxDist);

        float energy = clamp(kind * 0.85 + pulse * 0.10 + shimmer * 0.06, 0.0, 1.0);
        rgb = mix(abyssCrimson, deepBlood, clamp(0.30 + energy * 0.70, 0.0, 1.0));
        rgb = mix(rgb, richCrimson, clamp(core * (0.35 + energy * 0.50), 0.0, 1.0));
        rgb += rubyEdge * (rim * (0.20 + energy * 0.32) + core * core * energy * 0.18);

        outAlpha = alpha * (0.88 + 0.12 * rim);
    }

    if (outAlpha <= 0.01) discard;

    vec4 color = vec4(rgb, clamp(outAlpha, 0.0, 0.96)) * ColorModulator;
    float fragmentDistance = 1.0 / gl_FragCoord.w;
    fragColor = apply_fog(color, fragmentDistance, fragmentDistance,
                          FogEnvironmentalStart, FogEnvironmentalEnd,
                          FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
