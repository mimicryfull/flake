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

// Vertex color: r = kind, g = seed, b = pulse (heartbeat), a = alpha / dissolve.
// kind < 0.90  : opaque voxel (CORE pipeline), alpha = dissolve amount
// kind ~ 0.92  : soft mist (FOG pipeline, translucent)
// kind ~ 0.98  : hot glow sprite / ground pool (GLOW pipeline, additive)

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
    for (int i = 0; i < 3; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + vec2(17.1, 9.7);
        a *= 0.5;
    }
    return s;
}

// deep ruby -> crimson -> vivid blood red -> hot scarlet -> bright rose-red core
vec3 bloodRamp(float x) {
    const vec3 c0 = vec3(0.180, 0.008, 0.025);
    const vec3 c1 = vec3(0.420, 0.015, 0.045);
    const vec3 c2 = vec3(0.720, 0.030, 0.075);
    const vec3 c3 = vec3(0.950, 0.085, 0.135);
    const vec3 c4 = vec3(1.000, 0.260, 0.300);
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

    // Face normal from screen-space derivatives. Must precede any discard.
    vec3 N = normalize(cross(dFdx(relPos), dFdy(relPos)));
    vec3 V = normalize(-relPos);
    if (dot(N, V) < 0.0) N = -N;

    // ---- GLOW: additive hot sprite / ground pool ------------------------------------------
    if (kind >= 0.97) {
        vec2 p = texCoord0 * 2.0 - 1.0;
        float r2 = dot(p, p);
        if (r2 >= 1.0) discard;
        float r = sqrt(r2);
        float core = exp(-r2 * 6.0);
        float soft = (1.0 - r) * (1.0 - r);
        float flick = 0.88 + 0.12 * sin(sec * 9.0 + seed * 40.0);
        float a = alpha * (core * 1.55 + soft * 0.65) * (0.85 + 0.60 * pulse) * flick;
        vec3 c = mix(vec3(0.85, 0.04, 0.10), vec3(1.00, 0.28, 0.34), core);
        float fade = (1.0 - smoothstep(FogRenderDistanceStart, FogRenderDistanceEnd, cylindricalVertexDistance))
                   * (1.0 - smoothstep(FogEnvironmentalStart, FogEnvironmentalEnd, sphericalVertexDistance));
        fragColor = vec4(c * a * fade, 1.0);
        return;
    }

    vec3 rgb;
    float outAlpha;

    if (kind >= 0.90) {
        // ---- FOG: swirling blood mist with heartbeat swell ----------------------------------
        vec2 p = texCoord0 * 2.0 - 1.0;
        float r2 = dot(p, p);
        if (r2 >= 1.0) discard;
        float r = sqrt(r2);
        float ang = atan(p.y, p.x);
        float s = seed * 6.28318;
        vec2 q = vec2(cos(ang + sec * 0.18 + s), sin(ang + sec * 0.18 + s)) * (r * 1.6 + 0.4);
        float smoke = fbm(q + vec2(sec * 0.10, -sec * 0.07) + seed * 11.0);
        float body = pow(1.0 - r, 2.2) * (0.45 + 1.10 * smoke);
        float rk = (r - (0.30 + pulse * 0.34)) * 7.0;
        float ripple = exp(-rk * rk) * pulse * 0.55;
        float density = clamp((body + ripple) * (1.0 + pulse * 0.55), 0.0, 1.0);
        float core = pow(1.0 - r, 5.0) * smoke;
        rgb = bloodRamp(0.02 + density * 0.55 + core * 0.45 + pulse * 0.05);
        outAlpha = alpha * min(1.0, density * 2.2) * (1.0 - smoothstep(0.78, 1.0, r));
        if (outAlpha <= 0.01) discard;
    } else {
        // ---- CORE: opaque voxel ---------------------------------------------------------------
        vec2 d = abs(texCoord0 - 0.5) * 2.0;
        float b = max(d.x, d.y);
        float radial = length(d);

        float faceId = dot(abs(N), vec3(1.0, 2.0, 3.0)) + ((N.x + N.y + N.z) > 0.0 ? 7.0 : 0.0);
        vec2 g = floor(texCoord0 * 4.0);
        float n = h21(g + seed * 91.7 + faceId * 13.3);
        float flick = h21(g + seed * 53.1 + faceId * 7.7 + floor(sec * 3.0 + n * 5.0));

        // Dissolve: alpha < 1 eats the voxel texel by texel; the edge burns hot
        float thr = alpha * 1.18;
        if (n > thr) discard;
        float burn = alpha < 0.995 ? smoothstep(thr - 0.24, thr, n) : 0.0;

        float tier = clamp(kind, 0.0, 1.0);
        float hot = smoothstep(0.55, 0.80, tier);

        vec3 L = normalize(vec3(0.35, 0.85, 0.40));
        float diff = max(dot(N, L), 0.0);
        float faceTone = N.y > 0.5 ? 1.0 : (N.y < -0.5 ? 0.38 : 0.70);
        float light = 0.30 + 0.70 * diff * faceTone;

        float beat = pulse * (0.55 + 0.45 * sin(sec * 7.0 + seed * 40.0));
        float heat = tier + (n - 0.5) * 0.20 + beat * 0.14;
        vec3 base = bloodRamp(heat);

        float core = exp(-radial * radial * 2.2);
        float rimHi = smoothstep(0.58, 0.86, b) * (1.0 - smoothstep(0.90, 1.0, b));
        float edgeDark = smoothstep(0.90, 1.0, b);
        float fres = pow(1.0 - max(dot(N, V), 0.0), 3.0);

        rgb = base * (0.68 + light * 0.95) * 1.28;
        rgb += vec3(0.88, 0.05, 0.11) * rimHi * (0.42 + tier * 0.65 + beat * 0.35);
        rgb += vec3(0.68, 0.03, 0.10) * fres * (0.35 + tier * 0.60);
        rgb += vec3(0.95, 0.10, 0.16) * core * hot * (0.55 + 0.60 * beat);
        rgb += vec3(1.00, 0.18, 0.22) * step(0.84, flick) * hot * 0.70;
        rgb += vec3(0.95, 0.08, 0.14) * burn * 2.1;
        rgb *= 1.0 - edgeDark * 0.35;

        outAlpha = 1.0;
    }

    vec4 color = vec4(rgb, clamp(outAlpha, 0.0, 1.0)) * ColorModulator;
    float fragmentDistance = 1.0 / gl_FragCoord.w;
    fragColor = apply_fog(color, fragmentDistance, fragmentDistance,
                          FogEnvironmentalStart, FogEnvironmentalEnd,
                          FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
