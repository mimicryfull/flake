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

    vec3 darkCrimson = vec3(0.24, 0.006, 0.02);
    vec3 bloodRed = vec3(0.75, 0.025, 0.06);
    vec3 rubyGlow = vec3(1.00, 0.14, 0.18);
    vec3 hotCore = vec3(1.00, 0.48, 0.34);

    vec3 rgb;
    float outAlpha;

    if (kind >= 0.90) {
        // Soft atmospheric glow halo behind voxel clusters
        vec2 p = texCoord0 * 2.0 - 1.0;
        float r2 = dot(p, p);
        if (r2 >= 1.0) discard;
        float falloff = exp(-r2 * 4.0) * (1.0 - smoothstep(0.55, 1.0, sqrt(r2)));
        rgb = mix(bloodRed, rubyGlow, 0.5 + 0.35 * pulse);
        outAlpha = alpha * falloff;
    } else {
        // Glowing animated blood voxel face (UV 0..1 on each cube face)
        vec2 d = abs(texCoord0 - 0.5) * 2.0;
        float boxDist = max(d.x, d.y);
        float radial = length(d);

        float shimmer = 0.5 + 0.5 * sin(t * 1.4 + seed * 12.56637);
        float core = exp(-radial * radial * 2.2);
        float rim = smoothstep(0.68, 0.96, boxDist);

        // kind: 0.0 = dark outer/shed voxel, 0.5 = mid crimson voxel, 0.8 = bright kagune core voxel
        float energy = clamp(kind * 1.15 + pulse * 0.30 + shimmer * 0.18, 0.0, 1.0);
        rgb = mix(darkCrimson, bloodRed, clamp(0.35 + energy * 0.65, 0.0, 1.0));
        rgb = mix(rgb, rubyGlow, clamp(core * (0.45 + energy * 0.55) + rim * (0.28 + energy * 0.45), 0.0, 1.0));
        rgb += hotCore * (core * core * energy * 0.55);

        outAlpha = alpha * (0.86 + 0.14 * rim);
    }

    if (outAlpha <= 0.01) discard;

    vec4 color = vec4(rgb, clamp(outAlpha, 0.0, 0.98)) * ColorModulator;
    float fragmentDistance = 1.0 / gl_FragCoord.w;
    fragColor = apply_fog(color, fragmentDistance, fragmentDistance,
                          FogEnvironmentalStart, FogEnvironmentalEnd,
                          FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
