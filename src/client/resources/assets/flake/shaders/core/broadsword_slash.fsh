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

float h21(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x),
               mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    float fade = (1.0 - smoothstep(FogRenderDistanceStart, FogRenderDistanceEnd, cylindricalVertexDistance))
               * (1.0 - smoothstep(FogEnvironmentalStart, FogEnvironmentalEnd, sphericalVertexDistance));

    // texCoord0.x: 0 (tail) -> 1 (leading blade tip)
    // texCoord0.y: 0 (inner edge) -> 1 (outer blade edge)
    float along = clamp(texCoord0.x, 0.0, 1.0);
    float across = clamp(texCoord0.y, 0.0, 1.0);

    // Sharp outer blade edge, soft inner trail
    float edgeShape = smoothstep(0.0, 0.55, across) * (1.0 - smoothstep(0.84, 1.0, across));
    float tipTaper = smoothstep(0.0, 0.28, along) * (1.0 - smoothstep(0.92, 1.0, along));

    // Procedural slash streak texture
    float sec = GameTime * 1200.0;
    float streaks = vnoise(vec2(along * 18.0 - sec * 6.0, across * 9.0));
    float fine = vnoise(vec2(along * 34.0 - sec * 9.0, across * 16.0));
    float textureMask = 0.55 + 0.45 * streaks + 0.25 * fine;

    float coreLine = exp(-pow((across - 0.78) * 6.5, 2.0)) * smoothstep(0.15, 0.85, along);

    // Palette: dark red tail -> brown/orange body -> white-hot blade edge
    vec3 darkRed = vec3(0.55, 0.05, 0.04);
    vec3 orange  = vec3(1.00, 0.48, 0.09);
    vec3 white   = vec3(1.00, 0.96, 0.90);

    vec3 col = mix(darkRed, orange, smoothstep(0.05, 0.65, along) * 0.85 + across * 0.25);
    col = mix(col, white, clamp(coreLine * 0.95 + pow(along, 3.0) * 0.45, 0.0, 1.0));

    float a = vertexColor.a * edgeShape * tipTaper * textureMask * fade;
    if (a <= 0.004) discard;

    fragColor = vec4(col * a * 1.45, a);
}
