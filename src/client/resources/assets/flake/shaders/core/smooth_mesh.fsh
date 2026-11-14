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

    float outAlpha = clamp(vertexColor.a * fade, 0.0, 1.0);
    if (outAlpha <= 0.004) discard;

    // Mode 0: Smooth Kolobok sphere with analytical sphere normal + oval eyes & wide narrow smile
    if (texCoord0.x < 8.0) {
        float theta = texCoord0.x;
        float phi   = texCoord0.y;
        vec3 Nlocal = normalize(vec3(cos(phi) * sin(theta), sin(phi), cos(phi) * cos(theta)));
        vec3 Llocal = normalize(vec3(0.28, 0.84, 0.46));

        float ndl = clamp(dot(Nlocal, Llocal), 0.0, 1.0);
        float wrap = clamp(dot(Nlocal, Llocal) * 0.5 + 0.5, 0.0, 1.0);
        float bottomAO = smoothstep(-1.45, -0.35, phi) * 0.24 + 0.76;
        float rim = pow(1.0 - clamp(dot(Nlocal, vec3(0.0, 0.0, 1.0)), 0.0, 1.0), 2.8);

        // Warm matte golden-yellow shading matching the reference Kolobok
        vec3 baseCol = vertexColor.rgb * (0.64 + 0.34 * wrap + 0.12 * ndl) * bottomAO;

        // Paint crisp anti-aliased oval eyes, wide narrow smile & cheek crease ticks
        float faceMask = 0.0;
        if (abs(theta) < 1.35) {
            float fw = max(length(vec2(dFdx(theta), dFdy(theta))), 0.005);

            // Two centered vertical black oval eyes
            vec2 eL = vec2((theta + 0.255) / 0.088, (phi - 0.155) / 0.168);
            vec2 eR = vec2((theta - 0.255) / 0.088, (phi - 0.155) / 0.168);
            float eyeL = 1.0 - smoothstep(1.0 - fw * 5.5, 1.0 + fw * 5.5, length(eL));
            float eyeR = 1.0 - smoothstep(1.0 - fw * 5.5, 1.0 + fw * 5.5, length(eR));
            faceMask = max(faceMask, max(eyeL, eyeR));

            // Wide narrow U-shaped black smile sweeping between both cheeks
            float smileSpan = 0.70;
            float clampedT = clamp(theta, -smileSpan, smileSpan);
            float u = clampedT / smileSpan;
            float smileCurve = -0.39 + 0.31 * u * u;
            float dSmile = length(vec2(theta - clampedT, phi - smileCurve));
            float smile = 1.0 - smoothstep(0.022 - fw * 0.7, 0.022 + fw * 0.7, dSmile);
            faceMask = max(faceMask, smile);

            // Signature short diagonal crease ticks at both ends of the smile
            for (int side = -1; side <= 1; side += 2) {
                float sx = float(side);
                vec2 p0 = vec2(sx * 0.655, -0.055);
                vec2 p1 = vec2(sx * 0.735, -0.105);
                vec2 seg = p1 - p0;
                float h = clamp(dot(vec2(theta, phi) - p0, seg) / dot(seg, seg), 0.0, 1.0);
                float dCrease = length(vec2(theta, phi) - (p0 + seg * h));
                float crease = 1.0 - smoothstep(0.017 - fw * 0.7, 0.017 + fw * 0.7, dCrease);
                faceMask = max(faceMask, crease);
            }
        }

        vec3 blackInk = vec3(0.032, 0.032, 0.036);
        vec3 rgb = mix(baseCol, blackInk, faceMask) + vertexColor.rgb * (0.03 * rim * (1.0 - faceMask));
        fragColor = vec4(rgb, outAlpha);
        return;
    }

    // Mode 1 (UV.x > 8.0): Smooth non-voxel polygonal metal/wood geometry (no bevel edges)
    vec3 N = normalize(cross(dFdx(relPos), dFdy(relPos)));
    vec3 V = normalize(-relPos);
    if (dot(N, V) < 0.0) N = -N;

    vec3 L = normalize(vec3(0.35, 0.85, 0.42));
    vec3 H = normalize(L + V);
    float ndl = clamp(dot(N, L) * 0.5 + 0.5, 0.0, 1.0);
    float spec = pow(max(dot(N, H), 0.0), 18.0) * 0.22;
    vec3 rgb = vertexColor.rgb * (0.64 + 0.40 * ndl) + vec3(0.95, 0.90, 0.82) * spec;
    fragColor = vec4(rgb, outAlpha);
}
