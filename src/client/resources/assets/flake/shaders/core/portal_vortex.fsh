#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 portalUV;
layout(location = 3) in vec4 portalMeta;
layout(location = 4) in vec3 relPos;

layout(location = 0) out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm2(vec2 p) {
    float v = 0.0;
    v += 0.62 * vnoise(p);
    p = mat2(0.8, -0.6, 0.6, 0.8) * p * 2.07 + vec2(13.7, 9.2);
    v += 0.38 * vnoise(p);
    return v;
}

float starLayer(vec2 uv, float density) {
    vec2 g = floor(uv);
    vec2 f = fract(uv) - 0.5;
    float h = hash21(g);
    if (h < density) return 0.0;
    vec2 jitter = vec2(fract(h * 19.31), fract(h * 73.17)) - 0.5;
    float d = length(f - jitter * 0.55);
    float spark = pow(max(0.0, 1.0 - d * 4.4), 3.6);
    float twinkle = 0.65 + 0.35 * sin(GameTime * 1200.0 * 3.1 + h * 62.83);
    return spark * twinkle * (0.45 + 0.55 * fract(h * 37.9));
}

void faceBasis(int dir, out vec3 T, out vec3 B, out vec3 N) {
    if (dir == 0) {
        T = vec3(1.0, 0.0, 0.0); B = vec3(0.0, 0.0, 1.0); N = vec3(0.0, -1.0, 0.0);
    } else if (dir == 1) {
        T = vec3(1.0, 0.0, 0.0); B = vec3(0.0, 0.0, 1.0); N = vec3(0.0, 1.0, 0.0);
    } else if (dir == 2) {
        T = vec3(1.0, 0.0, 0.0); B = vec3(0.0, 1.0, 0.0); N = vec3(0.0, 0.0, -1.0);
    } else if (dir == 3) {
        T = vec3(1.0, 0.0, 0.0); B = vec3(0.0, 1.0, 0.0); N = vec3(0.0, 0.0, 1.0);
    } else if (dir == 4) {
        T = vec3(0.0, 0.0, 1.0); B = vec3(0.0, 1.0, 0.0); N = vec3(-1.0, 0.0, 0.0);
    } else {
        T = vec3(0.0, 0.0, 1.0); B = vec3(0.0, 1.0, 0.0); N = vec3(1.0, 0.0, 0.0);
    }
}

void main() {
    float fade = (1.0 - smoothstep(FogRenderDistanceStart, FogRenderDistanceEnd, cylindricalVertexDistance))
               * (1.0 - smoothstep(FogEnvironmentalStart, FogEnvironmentalEnd, sphericalVertexDistance));
    if (fade <= 0.004) discard;

    int dir = int(clamp(floor(portalMeta.b * 6.0), 0.0, 5.0));
    vec3 T, B, N;
    faceBasis(dir, T, B, N);

    vec3 V = normalize(relPos);
    float ndv = abs(dot(V, N));
    float invZ = 1.0 / max(ndv, 0.24);
    vec2 raySlope = vec2(dot(V, T), dot(V, B)) * invZ;
    float slopeLen = length(raySlope);
    if (slopeLen > 0.001) {
        raySlope *= (slopeLen / (1.0 + 0.14 * slopeLen)) / slopeLen;
    }

    float halfU = max(portalMeta.r * 16.0, 0.5);
    float halfV = max(portalMeta.g * 16.0, 0.5);
    float sizeScale = sqrt(halfU * halfV);
    vec2 aspect = vec2(halfU, halfV) / max(sizeScale, 0.5);
    vec2 shapeScale = mix(vec2(1.0), aspect, 0.45);

    vec2 pBase = portalUV * shapeScale;
    float portalDepth = clamp(sizeScale * 1.15, 0.80, 4.60);
    vec2 uvParallaxPerMeter = (raySlope / vec2(halfU, halfV)) * shapeScale;

    float time = GameTime * 1200.0;
    float seed = portalMeta.a;

    // Deep obsidian-violet cosmic abyss base
    vec3 col = vec3(0.022, 0.006, 0.058);
    float totalGlow = 0.0;

    // 8 parallax depth layers creating a true pseudo-3D swirling portal tunnel
    for (int i = 0; i < 8; i++) {
        float tLayer = (float(i) + 0.35) / 8.0;
        float zoom = 1.0 + tLayer * 2.15;
        float depthMeters = tLayer * portalDepth;

        vec2 q = (pBase + uvParallaxPerMeter * depthMeters) * zoom;
        float r = length(q);
        float ang = atan(q.y, q.x);

        float spin = -time * (0.90 + tLayer * 0.52) + 2.35 / (r + 0.32) + tLayer * 2.6 + seed * 6.28318;
        float cs = cos(spin);
        float sn = sin(spin);
        vec2 swirled = mat2(cs, -sn, sn, cs) * (q * (1.50 + tLayer * 0.55));

        float neb = fbm2(swirled + vec2(tLayer * 9.7, -time * 0.24));

        // Integer angular harmonics prevent any atan(-PI/+PI) seam
        float s3 = 0.5 + 0.5 * sin(3.0 * ang + 5.4 / (r + 0.38) - time * (1.65 + tLayer * 0.55) + neb * 2.7);
        float s5 = 0.5 + 0.5 * sin(5.0 * ang - 3.4 * r - time * 2.15 + tLayer * 3.2 - neb * 1.9);
        float filament = pow(clamp(s3 * 0.66 + s5 * 0.44, 0.0, 1.0), 2.35);

        float coreMask = smoothstep(0.06, 0.27, r);
        float accretion = exp(-pow((r - 0.31) / 0.26, 2.0));
        float outerMask = 1.0 - smoothstep(0.70, 1.75, r);

        float density = (filament * (0.45 + 0.65 * neb) + 0.28 * accretion) * coreMask * outerMask;

        // Parallax star flecks floating deep inside the vortex
        float stars = 0.0;
        if (i >= 1) {
            stars = starLayer(swirled * (4.2 + float(i) * 1.1) + vec2(tLayer * 17.0, 3.1), 0.90) * outerMask;
        }

        vec3 nearCol = vec3(0.76, 0.24, 1.00);
        vec3 midCol  = vec3(0.42, 0.10, 0.92);
        vec3 deepCol = vec3(0.26, 0.48, 1.00);
        vec3 layerTint = tLayer < 0.5
                ? mix(nearCol, midCol, tLayer * 2.0)
                : mix(midCol, deepCol, (tLayer - 0.5) * 2.0);

        float layerWeight = (1.0 - tLayer * 0.42) * 0.24;
        col += layerTint * (density * layerWeight);
        col += vec3(0.95, 0.72, 1.00) * (filament * accretion * layerWeight * 0.72);
        col += vec3(0.78, 0.86, 1.00) * (stars * (0.35 + 0.45 * tLayer));

        totalGlow += density * layerWeight;
    }

    // Parallax singularity core aligned to deep tunnel center
    vec2 deepCenter = pBase + uvParallaxPerMeter * (portalDepth * 0.92);
    float deepR = length(deepCenter) * 2.9;
    float coreBlackout = smoothstep(0.08, 0.42, deepR);
    float innerRing = exp(-pow((deepR - 0.42) / 0.15, 2.0));
    col = mix(vec3(0.012, 0.002, 0.032), col, 0.25 + 0.75 * coreBlackout);
    col += vec3(0.84, 0.52, 1.00) * (innerRing * 0.55);

    // Subtle glowing meniscus along outer multi-block border + Fresnel dimensional sheen
    float boxEdge = max(abs(portalUV.x), abs(portalUV.y));
    float borderPulse = 0.82 + 0.18 * sin(time * 2.4 + (portalUV.x + portalUV.y) * 3.5);
    float rimGlow = smoothstep(0.82, 1.0, boxEdge) * borderPulse;
    float fresnel = pow(1.0 - clamp(ndv, 0.0, 1.0), 3.0);

    col += vec3(0.68, 0.24, 0.98) * (rimGlow * 0.42 + fresnel * 0.30);

    float outAlpha = clamp(0.88 + 0.10 * clamp(totalGlow + rimGlow, 0.0, 1.0), 0.0, 0.98) * fade;
    fragColor = vec4(col, outAlpha);
}
