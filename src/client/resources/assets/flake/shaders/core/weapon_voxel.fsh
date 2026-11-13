#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;
layout(location = 4) in vec3 relPos;

layout(location = 0) out vec4 fragColor;

void main() {
    float fade = (1.0 - smoothstep(FogRenderDistanceStart, FogRenderDistanceEnd, cylindricalVertexDistance))
               * (1.0 - smoothstep(FogEnvironmentalStart, FogEnvironmentalEnd, sphericalVertexDistance));

    // UV in [2..3] encodes a soft radial glow sprite
    if (texCoord0.x > 1.5) {
        vec2 p = (texCoord0 - 2.0) * 2.0 - 1.0;
        float r2 = dot(p, p);
        if (r2 >= 1.0) discard;
        float core = exp(-r2 * 5.2);
        float soft = (1.0 - sqrt(r2));
        float a = vertexColor.a * (core * 0.95 + soft * soft * 0.35) * fade;
        fragColor = vec4(vertexColor.rgb * a, a);
        return;
    }

    // Crisp 3D voxel cube face with bevel + inner glow
    vec3 N = normalize(cross(dFdx(relPos), dFdy(relPos)));
    vec3 V = normalize(-relPos);
    if (dot(N, V) < 0.0) N = -N;

    vec2 uv = clamp(texCoord0, 0.0, 1.0);
    float edge = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    float bevel = smoothstep(0.0, 0.16, edge);
    vec2 centered = uv * 2.0 - 1.0;
    float innerGlow = exp(-dot(centered, centered) * 2.2) * 0.32;

    vec3 L = normalize(vec3(0.35, 0.85, 0.42));
    float ndl = clamp(dot(N, L) * 0.5 + 0.5, 0.0, 1.0);
    float shade = 0.68 + 0.36 * ndl;

    vec3 rgb = vertexColor.rgb * shade * (0.78 + 0.28 * bevel) + vertexColor.rgb * innerGlow;
    float outAlpha = clamp(vertexColor.a * fade, 0.0, 1.0);
    if (outAlpha <= 0.004) discard;

    fragColor = vec4(rgb, outAlpha);
}
