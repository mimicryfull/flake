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

float h11(float n) { return fract(sin(n * 127.1) * 43758.5453); }

float h21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x),
               mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float a = 0.5, s = 0.0;
    for (int i = 0; i < 4; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + vec2(17.1, 9.7);
        a *= 0.5;
    }
    return s;
}

vec3 bloodRamp(float x) {
    const vec3 c0 = vec3(0.09, 0.003, 0.012);
    const vec3 c1 = vec3(0.34, 0.009, 0.026);
    const vec3 c2 = vec3(0.74, 0.025, 0.058);
    const vec3 c3 = vec3(1.00, 0.110, 0.140);
    const vec3 c4 = vec3(1.00, 0.440, 0.320);
    x = clamp(x, 0.0, 1.0) * 4.0;
    if (x < 1.0) return mix(c0, c1, x);
    if (x < 2.0) return mix(c1, c2, x - 1.0);
    if (x < 3.0) return mix(c2, c3, x - 2.0);
    return mix(c3, c4, x - 3.0);
}

void main() {
    float kind = vertexColor.r;
    float seed = vertexColor.g;
    float pulse = vertexColor.b;
    float alpha = vertexColor.a;

    float sec = GameTime * 1200.0;

    // Face normal from screen-space derivatives (model space, camera at origin).
    // Must run before any discard to keep derivatives valid.
    vec3 dx = dFdx(relPos);
    vec3 dy = dFdy(relPos);
    vec3 N = normalize(cross(dx, dy));
    vec3 V = normalize(-relPos);
    if (dot(N, V) < 0.0) N = -N;

    vec3 rgb;
    float outAlpha;

    if (kind >= 0.90) {
        // Volumetric blood mist: swirling fbm smoke with heartbeat swell
        vec2 p = texCoord0 * 2.0 - 1.0;
        float r2 = dot(p, p);
        if (r2 >= 1.0) discard;
        float r = sqrt(r2);
        float ang = atan(p.y, p.x);
        float s = seed * 6.28318;
        vec2 q = vec2(cos(ang + sec * 0.18 + s), sin(ang + sec * 0.18 + s)) * (r * 1.6 + 0.4);
        float smoke = fbm(q + vec2(sec * 0.10, -sec * 0.07) + seed * 11.0);
        float body = pow(1.0 - r, 2.2) * (0.45 + 1.10 * smoke);
        float swell = 1.0 + pulse * 0.55;
        float ripple = exp(-pow((r - (0.30 + pulse * 0.34)) * 7.0, 2.0)) * pulse * 0.55;
        float density = clamp((body + ripple) * swell, 0.0, 1.0);
        float core = pow(1.0 - r, 4.0) * smoke;
        rgb = bloodRamp(0.18 + density * 0.72 + core * 0.65 + pulse * 0.10) * 1.25;
        outAlpha = alpha * density * (1.0 - smoothstep(0.78, 1.0, r));
    } else {
        // Voxel face: per-texel blood texture, directional light, fresnel rim, strong inner glow
        vec2 d = abs(texCoord0 - 0.5) * 2.0;
        float b = max(d.x, d.y);
        float radial = length(d);

        float faceId = dot(abs(N), vec3(1.0, 2.0, 3.0)) + ((N.x + N.y + N.z) > 0.0 ? 7.0 : 0.0);
        vec2 g = floor(texCoord0 * 4.0);
        float n = h21(g + seed * 91.7 + faceId * 13.3);
        float flick = h21(g + seed * 53.1 + faceId * 7.7 + floor(sec * 3.0 + n * 5.0));

        float tier = clamp(kind, 0.0, 1.0);
        float hot = smoothstep(0.42, 0.78, tier);

        vec3 L = normalize(vec3(0.35, 0.85, 0.40));
        float diff = max(dot(N, L), 0.0);
        float faceTone = N.y > 0.5 ? 1.0 : (N.y < -0.5 ? 0.48 : 0.78);
        float light = 0.55 + 0.55 * diff * faceTone;

        float beat = pulse * (0.55 + 0.45 * sin(sec * 7.0 + seed * 40.0));
        float heat = clamp(tier + 0.14 + (n - 0.5) * 0.20 + beat * 0.16, 0.0, 1.0);
        vec3 base = bloodRamp(heat);

        float innerGlow = exp(-radial * radial * 1.75);
        float core = exp(-radial * radial * 2.6);
        float rimHi = smoothstep(0.58, 0.86, b) * (1.0 - smoothstep(0.90, 1.0, b));
        float edgeDark = smoothstep(0.92, 1.0, b);
        float fres = pow(1.0 - max(dot(N, V), 0.0), 2.5);

        rgb = base * light;
        rgb += vec3(1.00, 0.14, 0.18) * innerGlow * (0.48 + tier * 0.65);
        rgb += vec3(1.00, 0.40, 0.30) * core * core * (0.30 + tier * 0.65 + 0.35 * beat);
        rgb += vec3(0.95, 0.10, 0.14) * rimHi * (0.28 + tier * 0.55);
        rgb += vec3(0.85, 0.07, 0.10) * fres * (0.28 + tier * 0.55);
        rgb += vec3(1.00, 0.24, 0.16) * core * hot * (0.45 + 0.55 * beat);
        rgb += vec3(1.00, 0.60, 0.52) * step(0.86, flick) * hot * 0.55;
        rgb *= 1.0 - edgeDark * 0.30;

        outAlpha = alpha * (0.95 + 0.05 * rimHi);
    }

    if (outAlpha <= 0.01) discard;

    vec4 color = vec4(rgb, clamp(outAlpha, 0.0, 0.97)) * ColorModulator;
    float fragmentDistance = 1.0 / gl_FragCoord.w;
    fragColor = apply_fog(color, fragmentDistance, fragmentDistance,
                          FogEnvironmentalStart, FogEnvironmentalEnd,
                          FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
