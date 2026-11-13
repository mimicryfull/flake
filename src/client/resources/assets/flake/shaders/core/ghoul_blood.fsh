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

    float t = GameTime * 180.0;

    vec3 obsidianBlood   = vec3(0.06, 0.002, 0.007);
    vec3 driedCrimson    = vec3(0.26, 0.006, 0.018);
    vec3 arterialCrimson = vec3(0.56, 0.016, 0.042);
    vec3 rubyAccent      = vec3(0.84, 0.065, 0.095);

    vec3 rgb;
    float outAlpha;

    if (kind >= 0.90) {
        // Subtle dark-crimson fog glow behind the voxel silhouette
        vec2 p = texCoord0 * 2.0 - 1.0;
        float r2 = dot(p, p);
        if (r2 >= 1.0) discard;
        float r = sqrt(r2);
        float angle = atan(p.y, p.x);
        float mist = 0.85 + 0.15 * sin(angle * 2.0 - t * 0.25 + r * 3.0 + seed * 6.28318);
        float falloff = exp(-r2 * 2.6) * (1.0 - smoothstep(0.50, 1.0, r));
        rgb = mix(obsidianBlood, driedCrimson, falloff) + arterialCrimson * (falloff * mist * 0.45);
        outAlpha = alpha * falloff * mist;
    } else {
        // Crisp dark-crimson voxel face with subtle ruby edge highlight (no strobing)
        vec2 d = abs(texCoord0 - 0.5) * 2.0;
        float boxDist = max(d.x, d.y);
        float radial = length(d);

        float core = exp(-radial * radial * 2.6);
        float rim = smoothstep(0.68, 0.97, boxDist);

        float tier = clamp(kind, 0.0, 1.0);
        rgb = mix(obsidianBlood, driedCrimson, clamp(0.25 + tier * 0.75, 0.0, 1.0));
        rgb = mix(rgb, arterialCrimson, clamp(tier * 0.85 * (0.45 + 0.55 * core), 0.0, 1.0));
        rgb += rubyAccent * (rim * (0.14 + tier * 0.36) + core * tier * tier * 0.28);

        outAlpha = alpha * (0.90 + 0.10 * rim);
    }

    if (outAlpha <= 0.01) discard;

    vec4 color = vec4(rgb, clamp(outAlpha, 0.0, 0.96)) * ColorModulator;
    float fragmentDistance = 1.0 / gl_FragCoord.w;
    fragColor = apply_fog(color, fragmentDistance, fragmentDistance,
                          FogEnvironmentalStart, FogEnvironmentalEnd,
                          FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
