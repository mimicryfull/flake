// Shaman VFX: turquoise Totem of Pain orb & shockwave + ground-projected Spirit Ward emerald pulsation.

const vec3 SHAMAN_TURQUOISE_DEEP = vec3(0.03, 0.56, 0.62);
const vec3 SHAMAN_TURQUOISE = vec3(0.10, 0.96, 0.88);
const vec3 SHAMAN_TURQUOISE_CORE = vec3(0.80, 1.00, 0.98);

const vec3 SHAMAN_EMERALD_DEEP = vec3(0.04, 0.58, 0.28);
const vec3 SHAMAN_EMERALD = vec3(0.18, 0.96, 0.50);
const vec3 SHAMAN_EMERALD_CORE = vec3(0.72, 1.00, 0.82);

void shamanRayPointGlow(vec3 ray, float sceneDistance, vec3 center, float coreRadius, float haloRadius,
                        float age, vec3 coreCol, vec3 haloCol, float intensity,
                        inout vec3 light, inout float chipAlpha, inout vec3 chipColor) {
    float along = dot(ray, center);
    if (along + haloRadius < 0.0 || along - coreRadius > sceneDistance) return;
    vec3 delta = ray * max(along, 0.0) - center;
    float distSq = dot(delta, delta);

    if (distSq < coreRadius * coreRadius && along <= sceneDistance + coreRadius) {
        float h = sqrt(max(0.0, coreRadius * coreRadius - distSq));
        float hitDist = along - h;
        if (hitDist > 0.0 && hitDist <= sceneDistance + 0.05) {
            vec3 n = normalize(ray * hitDist - center);
            float ndotv = clamp(-dot(n, ray), 0.0, 1.0);
            float fresnel = pow(1.0 - ndotv, 2.0);
            vec2 spUv = n.xz * 2.6 + vec2(n.y * 2.0 + age * 1.15, -age * 0.9);
            float plasma = 0.72 + 0.28 * softNoise(spUv * 2.4);
            vec3 surf = mix(coreCol, haloCol, fresnel * 0.75) * (0.85 + 0.25 * plasma);
            float a = clamp(intensity * (0.78 + 0.22 * ndotv), 0.0, 0.96);
            chipColor += surf * a;
            chipAlpha += a;
            light += haloCol * fresnel * intensity * 0.65;
        }
    }
    if (along > -0.25 && along <= sceneDistance + haloRadius) {
        float outerGlow = exp(-distSq / max(haloRadius * haloRadius * 0.24, 0.002));
        float midGlow = exp(-distSq / max(coreRadius * coreRadius * 1.65, 0.001));
        light += (haloCol * outerGlow * 0.48 + coreCol * midGlow * 0.65) * intensity;
    }
}

void shamanSmoothShell(vec3 ray, float sceneDistance, vec3 center, float radius, float thickness,
                       float alpha, vec3 innerCol, vec3 rimCol, inout vec3 light, inout vec2 distortion) {
    if (alpha <= 0.001 || radius <= 0.02) return;
    float along = dot(ray, center);
    float outerBound = radius + thickness * 1.6;
    if (along + outerBound < 0.0 || along - outerBound > sceneDistance) return;
    vec3 perp = ray * along - center;
    float d2 = dot(perp, perp);
    float r2 = radius * radius;

    if (d2 < outerBound * outerBound) {
        float rimDist = abs(sqrt(d2) - radius);
        float coronaBand = exp(-(rimDist * rimDist) / max(thickness * thickness * 1.8, 0.002));
        light += rimCol * coronaBand * alpha * 0.42;
        if (d2 < r2) {
            float halfChord = sqrt(max(0.0, r2 - d2));
            float tFront = along - halfChord;
            if (tFront > 0.0 && tFront < sceneDistance) {
                vec3 p = ray * tFront - center;
                vec3 n = normalize(p);
                float fresnel = pow(1.0 - abs(dot(n, ray)), 2.6);
                float shimmer = 0.68 + 0.32 * softNoise(p.xz * 2.2 + vec2(p.y * 1.7));
                light += mix(innerCol, rimCol, fresnel) * (0.16 + 0.84 * fresnel) * shimmer * alpha * 0.72;
                vec2 dir2 = length(perp.xy) > 0.0001 ? normalize(perp.xy) : vec2(0.0);
                distortion += dir2 * fresnel * alpha * 0.25;
            }
        }
    }
}

void shamanSceneRing(vec3 scenePoint, float depth, vec3 groundOrigin, float ringRadius, float width,
                     float alpha, vec3 col, inout vec3 light) {
    if (alpha <= 0.001 || depth <= 0.000001) return;
    vec3 rel = scenePoint - groundOrigin;
    if (abs(rel.y) > 1.85) return;
    float rXZ = length(rel.xz);
    float diff = (rXZ - ringRadius) / max(width, 0.01);
    if (abs(diff) > 3.2) return;
    float vertFade = exp(-rel.y * rel.y * 1.45);
    light += col * exp(-diff * diff) * vertFade * alpha;
}

void shamanTotemWave(vec3 ray, float sceneDistance, vec3 scenePoint, float depth, int slotIndex,
                     inout vec3 light, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    float alpha = WaveStyles[slotIndex].x;
    float age = WaveStyles[slotIndex].y;
    float waveAge = WaveStyles[slotIndex].w;
    vec3 origin = WaveOrigins[slotIndex].xyz;
    float zoneRadius = max(WaveOrigins[slotIndex].w, 1.0);

    if (!intersectsBound(ray, sceneDistance, origin + vec3(0.0, 1.0, 0.0), zoneRadius + 3.0)) return;

    // 1. Prominent glowing turquoise orb sphere above pedestal with vertical energy tether.
    float bob = sin(age * 3.2) * 0.06;
    vec3 orbPos = origin + vec3(0.0, 1.42 + bob, 0.0);
    float pulse = 0.90 + 0.10 * sin(age * 6.0);
    shamanRayPointGlow(ray, sceneDistance, orbPos, 0.30, 1.18, age,
                       SHAMAN_TURQUOISE_CORE, SHAMAN_TURQUOISE, alpha * pulse,
                       light, chipAlpha, chipColor);

    // Glowing vertical energy tether between pedestal collar (y+0.92) and orb bottom.
    vec3 colA = origin + vec3(0.0, 0.90, 0.0);
    vec3 colB = orbPos - vec3(0.0, 0.24, 0.0);
    vec3 u = colB - colA;
    float uLen2 = max(dot(u, u), 0.0001);
    vec3 w0 = -colA;
    float b = dot(ray, u);
    float denom = max(uLen2 - b * b, 0.0001);
    float tc = clamp((b * dot(ray, w0) - dot(u, w0)) / denom, 0.0, 1.0);
    float sc = max(0.0, dot(ray, colA + u * tc));
    if (sc <= sceneDistance + 0.1) {
        vec3 closestDiff = (ray * sc) - (colA + u * tc);
        float d2 = dot(closestDiff, closestDiff);
        float beam = exp(-d2 / 0.0065) * 0.80 + exp(-d2 / 0.032) * 0.34;
        light += mix(SHAMAN_TURQUOISE, SHAMAN_TURQUOISE_CORE, exp(-d2 / 0.003)) * beam * alpha * 0.72;
    }

    // 2. Smooth Concussed-style expanding turquoise explosion shockwave + surface wave.
    if (waveAge < 0.85) {
        float wT = clamp(waveAge / 0.85, 0.0, 1.0);
        float eased = 1.0 - pow(1.0 - wT, 3.0);
        float shellR = mix(0.35, zoneRadius + 0.40, eased);
        float shellAlpha = alpha * pow(1.0 - wT, 1.2);
        shamanSmoothShell(ray, sceneDistance, origin + vec3(0.0, 0.65, 0.0), shellR, 0.25,
                          shellAlpha, SHAMAN_TURQUOISE_DEEP, SHAMAN_TURQUOISE_CORE, light, distortion);
        shamanSceneRing(scenePoint, depth, origin, shellR, 0.32, shellAlpha * 0.90, SHAMAN_TURQUOISE_CORE, light);
    }

    // 3. Depth-projected ground zone boundary ring + 1s damage-tick pulse + soft zone mist (zero z-fighting!).
    shamanSceneRing(scenePoint, depth, origin, zoneRadius, 0.20,
                    alpha * (0.64 + 0.16 * sin(age * 4.5)), SHAMAN_TURQUOISE, light);
    float tickCycle = fract(age);
    float tickRadius = mix(0.25, zoneRadius, tickCycle);
    float tickAlpha = (1.0 - tickCycle) * 0.62 * alpha;
    shamanSceneRing(scenePoint, depth, origin, tickRadius, 0.24, tickAlpha, SHAMAN_TURQUOISE_CORE, light);

    if (depth > 0.000001) {
        vec3 floorRel = scenePoint - origin;
        float horizDist = length(floorRel.xz);
        if (horizDist < zoneRadius && abs(floorRel.y) < 1.65) {
            float radialMask = 1.0 - smoothstep(zoneRadius * 0.60, zoneRadius, horizDist);
            float edgeGlow = exp(-pow((horizDist - zoneRadius) / 0.28, 2.0));
            float vertMask = exp(-floorRel.y * floorRel.y * 2.2);
            float swirl = 0.68 + 0.32 * softNoise(floorRel.xz * 1.4 + vec2(age * 0.7, -age * 0.5));
            light += mix(SHAMAN_TURQUOISE_DEEP, SHAMAN_TURQUOISE, edgeGlow)
                     * (0.13 * radialMask + 0.28 * edgeGlow) * vertMask * swirl * alpha;
        }
    }
}

void shamanWardWave(vec3 ray, float sceneDistance, vec3 scenePoint, float depth, int slotIndex,
                    inout vec3 light, inout vec2 distortion) {
    float alpha = WaveStyles[slotIndex].x;
    float age = WaveStyles[slotIndex].y;
    float jumpHeight = max(0.0, WaveStyles[slotIndex].w);
    vec3 groundOrigin = WaveOrigins[slotIndex].xyz;
    float radius = max(WaveOrigins[slotIndex].w, 1.0);

    if (!intersectsBound(ray, sceneDistance, groundOrigin + vec3(0.0, jumpHeight * 0.5 + 1.0, 0.0), radius + jumpHeight + 2.5)) return;

    // 1. Expanding initial emerald wave on cast.
    if (age < 0.60) {
        float t = clamp(age / 0.60, 0.0, 1.0);
        float shellR = mix(0.4, radius + 0.2, 1.0 - pow(1.0 - t, 3.0));
        shamanSmoothShell(ray, sceneDistance, groundOrigin + vec3(0.0, 0.45 + jumpHeight * 0.5, 0.0),
                          shellR, 0.18, alpha * (1.0 - t) * 0.72,
                          SHAMAN_EMERALD_DEEP, SHAMAN_EMERALD_CORE, light, distortion);
    }

    // 2. Depth-projected green floor pulsation beneath player (clean over uneven terrain & during jumps).
    float beat1 = fract(age * 1.15);
    float beat2 = fract(age * 1.15 + 0.5);
    shamanSceneRing(scenePoint, depth, groundOrigin,
                    mix(0.24, radius * 0.96, beat1), 0.24, alpha * (1.0 - beat1) * 0.82, SHAMAN_EMERALD_CORE, light);
    shamanSceneRing(scenePoint, depth, groundOrigin,
                    mix(0.24, radius * 0.96, beat2), 0.20, alpha * (1.0 - beat2) * 0.52, SHAMAN_EMERALD, light);
    shamanSceneRing(scenePoint, depth, groundOrigin, radius,
                    0.20, alpha * (0.60 + 0.18 * sin(age * 5.0)), SHAMAN_EMERALD, light);

    // Soft emerald underglow pool on the floor directly beneath player's XZ position.
    if (depth > 0.000001) {
        vec3 rel = scenePoint - groundOrigin;
        float rXZ = length(rel.xz);
        if (rXZ < radius && abs(rel.y) < 1.85) {
            float heart = 0.70 + 0.30 * sin(age * 6.5);
            float centerPool = exp(-(rXZ * rXZ) / 1.65) * 0.38;
            float ringFade = smoothstep(radius, radius * 0.50, rXZ) * 0.12;
            float yMask = exp(-rel.y * rel.y * 1.85);
            light += mix(SHAMAN_EMERALD_DEEP, SHAMAN_EMERALD_CORE, exp(-(rXZ * rXZ) / 0.75))
                     * (centerPool + ringFade) * yMask * heart * alpha;
        }
    }

    // 3. Soft emerald body aura & vertical light column while jumping.
    vec3 bodyCenter = groundOrigin + vec3(0.0, jumpHeight + 0.85, 0.0);
    float along = dot(ray, bodyCenter);
    if (along + 1.6 > 0.0 && along - 1.6 < sceneDistance) {
        vec3 delta = ray * max(along, 0.0) - bodyCenter;
        float d2 = dot(delta, delta);
        light += SHAMAN_EMERALD * exp(-d2 / 0.95) * 0.18 * alpha;
    }
    if (jumpHeight > 0.05) {
        vec3 u = vec3(0.0, jumpHeight + 0.55, 0.0);
        float uLen2 = dot(u, u);
        vec3 w0 = -groundOrigin;
        float b = dot(ray, u);
        float denom = max(uLen2 - b * b, 0.0001);
        float tc = clamp((b * dot(ray, w0) - dot(u, w0)) / denom, 0.0, 1.0);
        float sc = max(0.0, dot(ray, groundOrigin + u * tc));
        if (sc <= sceneDistance + 0.1) {
            vec3 cd = (ray * sc) - (groundOrigin + u * tc);
            float d2 = dot(cd, cd);
            float tether = exp(-d2 / 0.11) * 0.28 * (1.0 - tc * 0.40);
            light += SHAMAN_EMERALD * tether * alpha;
        }
    }
}
