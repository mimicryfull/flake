// Post-shader beams, lightning discharges, spectral lash, void singularity and purify pulse for combat items & weapons.

const vec3 LASH_CRIMSON_DARK = vec3(0.32, 0.02, 0.06);
const vec3 LASH_CRIMSON = vec3(0.96, 0.14, 0.24);
const vec3 LASH_ROSE_CORE = vec3(1.00, 0.72, 0.60);

const vec3 PURIFY_CYAN = vec3(0.36, 0.96, 1.00);
const vec3 PURIFY_GOLD = vec3(1.00, 0.88, 0.42);

const vec3 DRONE_ORANGE = vec3(1.00, 0.44, 0.08);
const vec3 DRONE_AMBER  = vec3(1.00, 0.72, 0.16);
const vec3 DRONE_YELLOW = vec3(1.00, 0.94, 0.48);

void vfxClosestRaySegment(vec3 ray, vec3 a, vec3 b, out float rayDist, out float segT, out float perpDist) {
    vec3 u = b - a;
    vec3 w0 = -a;
    float uu = max(dot(u, u), 0.00001);
    float bu = dot(ray, u);
    float denom = max(uu - bu * bu, 0.00001);
    segT = clamp((bu * dot(ray, w0) - dot(u, w0)) / denom, 0.0, 1.0);
    vec3 segPt = a + u * segT;
    rayDist = max(0.0, dot(ray, segPt));
    vec3 diff = ray * rayDist - segPt;
    perpDist = length(diff);
}

void vfxPointFlash(vec3 ray, float sceneDistance, vec3 center, float radius, float intensity,
                   vec3 coreCol, vec3 haloCol, inout vec3 light, inout float chipAlpha, inout vec3 chipColor) {
    if (intensity <= 0.001) return;
    float along = dot(ray, center);
    if (along + radius < 0.0 || along - radius * 0.25 > sceneDistance) return;
    vec3 diff = ray * max(along, 0.0) - center;
    float d2 = dot(diff, diff);
    float core = exp(-d2 / max(radius * radius * 0.025, 0.0005));
    float halo = exp(-d2 / max(radius * radius * 0.28, 0.002));
    light += (coreCol * core * 0.95 + haloCol * halo * 0.55) * intensity;
    float c = core * clamp(intensity, 0.0, 1.0) * 0.85;
    chipColor += coreCol * c;
    chipAlpha += c;
}

// Style 20: Storm Staff jagged lightning (mode < 0.5) or Storm Harpoon cyan HDR laser + arcs (mode >= 0.5).
void stormBeamWave(vec3 ray, float sceneDistance, int slotIndex,
                   inout vec3 light, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    float alpha = WaveStyles[slotIndex].x;
    float age = WaveStyles[slotIndex].y;
    float mode = WaveStyles[slotIndex].w;
    vec3 boundCenter = WaveOrigins[slotIndex].xyz;
    float boundRadius = max(WaveOrigins[slotIndex].w, 1.0);
    if (!intersectsBound(ray, sceneDistance, boundCenter, boundRadius + 2.0)) return;

    int segBase = slotIndex * 64;
    if (mode < 0.5) {
        // Storm Staff: multi-segment volumetric shader lightning (Concussed Skill 2 style).
        int count = clamp(int(QuakeMaterials[slotIndex * 32].x + 0.5), 0, 64);
        for (int s = 0; s < count; s++) {
            vec4 st = QuakeStarts[segBase + s];
            vec4 en = QuakeEnds[segBase + s];
            discharge(ray, sceneDistance, st.xyz, en.xyz, st.w, alpha * en.w, light);
        }
        if (count > 0) {
            vec3 muzzle = QuakeStarts[segBase].xyz;
            vec3 tip = QuakeEnds[segBase + count - 1].xyz;
            float flash = exp(-age * 9.0) * alpha;
            vfxPointFlash(ray, sceneDistance, muzzle, 0.9, flash * 0.85, ELECTRIC_CORE, ELECTRIC_CYAN, light, chipAlpha, chipColor);
            vfxPointFlash(ray, sceneDistance, tip, 1.4, flash * 1.15, ELECTRIC_CORE, ELECTRIC_CYAN, light, chipAlpha, chipColor);
        }
        return;
    }

    // Storm Harpoon: blazing HDR cyan-white laser beam + bloom + corona flash + plasma arcs.
    vec3 a = QuakeStarts[segBase].xyz;
    vec3 b = QuakeEnds[segBase].xyz;
    float beamWidth = max(QuakeStarts[segBase].w, 0.03);
    float impactFlash = QuakeEnds[segBase].w;

    float rayDist, segT, perp;
    vfxClosestRaySegment(ray, a, b, rayDist, segT, perp);
    if (rayDist <= sceneDistance + 0.15) {
        float packet = 0.72 + 0.28 * sin(segT * 28.0 + age * 26.0);
        float core = exp(-(perp * perp) / (beamWidth * beamWidth * 0.55)) * packet;
        float innerBloom = exp(-(perp * perp) / 0.032);
        float outerBloom = exp(-(perp * perp) / 0.28);

        light += (ELECTRIC_CORE * core * 0.90 + ELECTRIC_CYAN * innerBloom * 0.75 + ELECTRIC_BLUE * outerBloom * 0.36) * alpha;
        float c = clamp(core * alpha * 0.90, 0.0, 0.95);
        chipColor += ELECTRIC_CORE * c;
        chipAlpha += c;

        vec2 scrA = screenPosition(a), scrB = screenPosition(b);
        vec2 dir2 = scrB - scrA;
        vec2 n2 = length(dir2) > 0.0001 ? vec2(-dir2.y, dir2.x) / length(dir2) : vec2(0.0);
        distortion += n2 * exp(-(perp * perp) / 0.08) * sin(segT * 20.0 - age * 18.0) * alpha * 0.18;
    }

    int arcCount = clamp(int(QuakeMaterials[slotIndex * 32].x + 0.5), 0, 12);
    for (int k = 1; k <= arcCount; k++) {
        vec4 st = QuakeStarts[segBase + k];
        vec4 en = QuakeEnds[segBase + k];
        discharge(ray, sceneDistance, st.xyz, en.xyz, st.w, alpha * en.w, light);
    }
    vfxPointFlash(ray, sceneDistance, b, 1.5, (0.55 + impactFlash * 0.90) * alpha, ELECTRIC_CORE, ELECTRIC_CYAN, light, chipAlpha, chipColor);
}

// Style 21: Sleek crimson-obsidian energy lash for Chain Blade (zero blocky cubes).
void chainLashWave(vec3 ray, float sceneDistance, int slotIndex,
                   inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    float alpha = WaveStyles[slotIndex].x;
    float age = WaveStyles[slotIndex].y;
    vec3 boundCenter = WaveOrigins[slotIndex].xyz;
    float boundRadius = max(WaveOrigins[slotIndex].w, 1.0);
    if (!intersectsBound(ray, sceneDistance, boundCenter, boundRadius + 1.5)) return;

    int count = clamp(int(QuakeMaterials[slotIndex * 32].x + 0.5), 0, 32);
    int segBase = slotIndex * 64;
    float minCore = 1e6;
    float bestT = 0.0;
    float totalGlow = 0.0;
    float linkSpark = 0.0;

    for (int s = 0; s < count; s++) {
        vec3 a = QuakeStarts[segBase + s].xyz;
        vec3 b = QuakeEnds[segBase + s].xyz;
        float w = max(QuakeStarts[segBase + s].w, 0.018);
        float rDist, t, perp;
        vfxClosestRaySegment(ray, a, b, rDist, t, perp);
        if (rDist > sceneDistance + 0.12) continue;

        float u = (float(s) + t) / max(float(count), 1.0);
        float core = exp(-(perp * perp) / (w * w));
        float halo = exp(-(perp * perp) / (w * w * 18.0));
        float glint = pow(max(0.0, sin(u * 38.0 - age * 14.0)), 6.0) * exp(-(perp * perp) / (w * w * 3.5));

        totalGlow = max(totalGlow, halo);
        linkSpark = max(linkSpark, glint);
        if (perp < minCore) {
            minCore = perp;
            bestT = core;
        }
    }

    // Subtle dark obsidian contrast sheath around the lash so crimson filament pops vividly.
    darkness += totalGlow * alpha * 0.24;
    vec3 lashCol = mix(LASH_CRIMSON_DARK, LASH_CRIMSON, totalGlow);
    light += (lashCol * totalGlow * 0.72 + LASH_ROSE_CORE * linkSpark * 0.85) * alpha;

    float coreMask = clamp((bestT + linkSpark * 0.55) * alpha, 0.0, 0.95);
    if (coreMask > 0.01) {
        vec3 coreCol = mix(LASH_CRIMSON, LASH_ROSE_CORE, clamp(bestT * 0.8 + linkSpark * 0.5, 0.0, 1.0));
        chipColor += coreCol * coreMask;
        chipAlpha += coreMask;
    }

    if (count > 0) {
        vec3 hookPos = QuakeEnds[segBase + count - 1].xyz;
        float latchFlash = (0.45 + 0.85 * exp(-age * 7.5)) * alpha;
        vfxPointFlash(ray, sceneDistance, hookPos, 1.05, latchFlash, LASH_ROSE_CORE, LASH_CRIMSON, light, chipAlpha, chipColor);
        float hAlong = dot(ray, hookPos);
        if (hAlong > 0.0 && hAlong < sceneDistance + 0.5) {
            vec2 delta = (screenPosition(hookPos) - texCoord);
            float dl = length(delta);
            if (dl > 0.0001 && dl < 0.22) {
                distortion -= normalize(delta) * exp(-dl * dl * 90.0) * exp(-age * 6.0) * alpha * 0.30;
            }
        }
    }
}

// Style 22: Void Anchor gravitational singularity, suction distortion & purple vortex.
void voidAnchorWave(vec3 ray, float sceneDistance, vec3 scenePoint, float depth, int slotIndex,
                    inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    float alpha = WaveStyles[slotIndex].x;
    float age = WaveStyles[slotIndex].y;
    float pullBurst = WaveStyles[slotIndex].w;
    vec3 origin = WaveOrigins[slotIndex].xyz;
    float radius = max(WaveOrigins[slotIndex].w, 1.0);

    if (!intersectsBound(ray, sceneDistance, origin, radius + 2.5)) return;

    float along = dot(ray, origin);
    if (along > 0.0 && along < sceneDistance + 1.5) {
        vec3 perpVec = ray * along - origin;
        float d2 = dot(perpVec, perpVec);
        float d = sqrt(d2);

        // Darkened black-hole singularity core + glowing violet accretion corona.
        float coreRadius = 0.24 + pullBurst * 0.22;
        if (d < coreRadius && along - coreRadius <= sceneDistance) {
            float bh = smoothstep(coreRadius, coreRadius * 0.35, d);
            darkness += bh * alpha * 0.78;
        }
        float rim = exp(-pow((d - coreRadius) / 0.14, 2.0));
        float halo = exp(-d2 / 0.75);
        light += (VOID_BRIGHT * rim * 0.90 + VOID_VIOLET * halo * 0.45) * alpha;

        // Gravitational lensing pull toward the anchor center.
        vec2 scOrigin = screenPosition(origin);
        vec2 toCenter = scOrigin - texCoord;
        float scLen = length(toCenter);
        if (scLen > 0.0005 && scLen < 0.45) {
            float pullStrength = exp(-d2 / (2.2 + pullBurst * 4.0)) * (0.28 + pullBurst * 0.55) * alpha;
            distortion += normalize(toCenter) * pullStrength;
        }
    }

    // Collapsing floor vortex rings during 1.5s charge, explosive violet shockwave on pull.
    float chargeRingR = mix(radius, 0.55, clamp(fract(age * 0.9), 0.0, 1.0));
    goldenFloorRing(ray, sceneDistance, origin + vec3(0.0, 0.05, 0.0), radius, 0.16, alpha * 0.55, VOID_VIOLET, light);
    goldenFloorRing(ray, sceneDistance, origin + vec3(0.0, 0.06, 0.0), chargeRingR, 0.22, alpha * 0.65, VOID_BRIGHT, light);

    if (pullBurst > 0.01) {
        float burstR = mix(0.4, radius + 0.5, 1.0 - pullBurst);
        shamanSmoothShell(ray, sceneDistance, origin, burstR, 0.25, pullBurst * alpha * 0.85,
                          VOID_VIOLET, VOID_BRIGHT, light, distortion);
    }

    // Soft void mist on ground inside the 5m pull radius.
    if (depth > 0.000001) {
        vec3 rel = scenePoint - origin;
        float rXZ = length(rel.xz);
        if (rXZ < radius && abs(rel.y) < 1.5) {
            float falloff = 1.0 - smoothstep(0.5, radius, rXZ);
            float swirl = 0.65 + 0.35 * softNoise(rel.xz * 1.5 + vec2(age * 1.4, -age * 1.1));
            light += VOID_VIOLET * falloff * exp(-rel.y * rel.y * 2.5) * swirl * (0.14 + pullBurst * 0.20) * alpha;
        }
    }
}

// Style 23: Spark Syringe golden-cyan purifier aura & rising cleanse shell.
void sparkSyringeWave(vec3 ray, float sceneDistance, int slotIndex,
                      inout vec3 light, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    float alpha = WaveStyles[slotIndex].x;
    float age = WaveStyles[slotIndex].y;
    vec3 origin = WaveOrigins[slotIndex].xyz;
    float radius = max(WaveOrigins[slotIndex].w, 1.0);
    if (!intersectsBound(ray, sceneDistance, origin + vec3(0.0, 0.9, 0.0), radius + 1.5)) return;

    float t = clamp(age / 0.85, 0.0, 1.0);
    float ringR = mix(0.35, radius, 1.0 - pow(1.0 - t, 2.5));
    goldenFloorRing(ray, sceneDistance, origin + vec3(0.0, 0.04, 0.0), ringR, 0.22, alpha * (1.0 - t * 0.7), PURIFY_CYAN, light);

    float sweepH = t * 2.1;
    vec3 coreCenter = origin + vec3(0.0, sweepH, 0.0);
    vfxPointFlash(ray, sceneDistance, coreCenter, 1.1, alpha * (1.0 - t * 0.65) * 0.72, PURIFY_GOLD, PURIFY_CYAN, light, chipAlpha, chipColor);
    shamanSmoothShell(ray, sceneDistance, origin + vec3(0.0, 0.95, 0.0), mix(0.4, 1.65, t), 0.16,
                      alpha * (1.0 - t) * 0.55, PURIFY_CYAN, PURIFY_GOLD, light, distortion);
}

// Style 24: Combat Drone orange-yellow deployment shockwave, antenna beacon halo & melee strike bursts.
void combatDroneWave(vec3 ray, float sceneDistance, vec3 scenePoint, float depth, int slotIndex,
                     inout vec3 light, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    float alpha = WaveStyles[slotIndex].x;
    float age = WaveStyles[slotIndex].y;
    float hitAge = WaveStyles[slotIndex].w;
    vec3 origin = WaveOrigins[slotIndex].xyz;
    float radius = max(WaveOrigins[slotIndex].w, 1.5);
    if (!intersectsBound(ray, sceneDistance, origin + vec3(0.0, 0.45, 0.0), radius + 2.0)) return;

    // Warm orange-yellow activation shockwave over first 0.72s
    if (age < 0.72) {
        float spawnT = clamp(age / 0.72, 0.0, 1.0);
        float easeOut = 1.0 - pow(1.0 - spawnT, 2.6);
        float fade = (1.0 - spawnT) * alpha;
        float ringR = mix(0.28, 2.35, easeOut);
        float innerR = mix(0.18, 1.55, easeOut);
        shamanSceneRing(scenePoint, depth, origin, ringR, 0.24, fade * 0.95, DRONE_ORANGE, light);
        shamanSceneRing(scenePoint, depth, origin, innerR, 0.16, fade * 0.85, DRONE_YELLOW, light);

        vec3 burstCore = origin + vec3(0.0, 0.34, 0.0);
        vfxPointFlash(ray, sceneDistance, burstCore, 1.35, fade * 1.10, DRONE_YELLOW, DRONE_ORANGE,
                      light, chipAlpha, chipColor);
        shamanSmoothShell(ray, sceneDistance, burstCore, mix(0.25, 1.85, easeOut), 0.16,
                          fade * 0.62, DRONE_ORANGE, DRONE_YELLOW, light, distortion);
    }

    // Persistent pulsing antenna beacon halo & subtle amber ground underglow
    float pulse = 0.78 + 0.22 * sin(age * 12.0);
    vec3 beaconPos = origin + vec3(0.0, 0.62, 0.0);
    vfxPointFlash(ray, sceneDistance, beaconPos, 0.58, alpha * 0.48 * pulse, DRONE_YELLOW, DRONE_AMBER,
                  light, chipAlpha, chipColor);
    if (depth > 0.000001) {
        vec3 rel = scenePoint - origin;
        float rXZ = length(rel.xz);
        if (rXZ < 1.25 && abs(rel.y) < 1.35) {
            float gGlow = exp(-rXZ * rXZ * 3.2) * exp(-rel.y * rel.y * 4.5);
            light += mix(DRONE_ORANGE, DRONE_AMBER, 0.55) * gGlow * (0.20 * pulse) * alpha;
        }
    }

    // Orange-yellow melee hit burst & shock ripple when the drone strikes
    if (hitAge >= 0.0 && hitAge < 0.42) {
        float hT = clamp(hitAge / 0.42, 0.0, 1.0);
        float hFade = (1.0 - hT) * alpha;
        int segBase = slotIndex * 64;
        vec3 hitCenter = QuakeStarts[segBase].x > 0.5 ? QuakeEnds[segBase].xyz : (origin + vec3(0.0, 0.45, 0.0));
        vfxPointFlash(ray, sceneDistance, hitCenter, 1.25, hFade * 1.15, DRONE_YELLOW, DRONE_ORANGE,
                      light, chipAlpha, chipColor);
        float hitRingR = mix(0.20, 1.45, 1.0 - pow(1.0 - hT, 2.2));
        shamanSceneRing(scenePoint, depth, vec3(hitCenter.x, origin.y, hitCenter.z),
                        hitRingR, 0.18, hFade * 0.90, DRONE_AMBER, light);

        float hAlong = dot(ray, hitCenter);
        if (hAlong > 0.0 && hAlong < sceneDistance + 0.5) {
            vec2 delta = screenPosition(hitCenter) - texCoord;
            float dl = length(delta);
            if (dl > 0.0001 && dl < 0.18) {
                distortion += normalize(delta) * exp(-dl * dl * 110.0) * hFade * 0.20;
            }
        }
    }
}

// Style 26: Kolobok golden spawn pulse & AK-47 gunfire muzzle/tracer/impact bursts.
void kolobokWave(vec3 ray, float sceneDistance, vec3 scenePoint, float depth, int slotIndex,
                 inout vec3 light, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    float alpha = WaveStyles[slotIndex].x;
    float age = WaveStyles[slotIndex].y;
    float shotAge = WaveStyles[slotIndex].w;
    vec3 origin = WaveOrigins[slotIndex].xyz;
    float radius = max(WaveOrigins[slotIndex].w, 2.0);
    if (!intersectsBound(ray, sceneDistance, origin + vec3(0.0, 0.4, 0.0), radius + 2.0)) return;

    if (age < 0.55) {
        float spawnT = clamp(age / 0.55, 0.0, 1.0);
        float fade = (1.0 - spawnT) * alpha;
        float ringR = mix(0.22, 1.75, 1.0 - pow(1.0 - spawnT, 2.4));
        shamanSceneRing(scenePoint, depth, origin, ringR, 0.20, fade * 0.88, DRONE_YELLOW, light);
    }

    if (depth > 0.000001) {
        vec3 rel = scenePoint - origin;
        float rXZ = length(rel.xz);
        if (rXZ < 1.15 && abs(rel.y) < 1.2) {
            light += DRONE_AMBER * exp(-rXZ * rXZ * 3.5) * exp(-rel.y * rel.y * 4.5) * 0.16 * alpha;
        }
    }

    if (shotAge >= 0.0 && shotAge < 0.24) {
        float sT = clamp(shotAge / 0.24, 0.0, 1.0);
        float sFade = (1.0 - sT) * alpha;
        int segBase = slotIndex * 64;
        if (QuakeStarts[segBase].w > 0.5) {
            vec3 muzzle = QuakeStarts[segBase].xyz;
            vec3 hitPos = QuakeEnds[segBase].xyz;
            vfxPointFlash(ray, sceneDistance, muzzle, 0.85, sFade * 1.10, DRONE_YELLOW, DRONE_ORANGE,
                          light, chipAlpha, chipColor);
            vfxPointFlash(ray, sceneDistance, hitPos, 0.90, sFade * 0.95, DRONE_YELLOW, DRONE_ORANGE,
                          light, chipAlpha, chipColor);

            float rDist, segT, perp;
            vfxClosestRaySegment(ray, muzzle, hitPos, rDist, segT, perp);
            if (rDist <= sceneDistance + 0.15) {
                float core = exp(-(perp * perp) / 0.0012);
                float halo = exp(-(perp * perp) / 0.018);
                light += (DRONE_YELLOW * core * 0.90 + DRONE_ORANGE * halo * 0.55) * sFade;
                float c = core * sFade * 0.65;
                chipColor += DRONE_YELLOW * c;
                chipAlpha += c;
            }
        }
    }
}
