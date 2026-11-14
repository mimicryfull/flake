// Class: Bastion ("Бастіон") — Skill 2: Bulwark ("Нерухомий бастіон")
// Lightweight golden energy dome, edge pillars, ground lock ring & reactive hit shockwave paired with 3D voxel fortress plates.

void bulwarkAura(vec3 ray, float sceneDistance, int wave, bool owner,
                 inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    vec4 body = QuakeBounds[wave * 8];
    if (body.w <= 0.0) return;
    vec3 feet = body.xyz;
    float height = body.w;
    float alpha = WaveStyles[wave].x;
    float age = WaveStyles[wave].y;
    vec4 params = QuakeMaterials[wave * 32];
    vec4 incoming = QuakeMaterials[wave * 32 + 1];
    float offAt = QuakeMaterials[wave * 32 + 2].x;
    vec3 mid = feet + vec3(0.0, height * 0.52, 0.0);
    if (!intersectsBound(ray, sceneDistance, mid, height * 0.65 + 1.85)) return;

    float exitAge = max(0.0, age - offAt);
    float release = smoothstep(0.0, 0.62, exitAge);
    float interior = owner ? 0.36 + 0.64 * smoothstep(0.18, 0.62, length(texCoord - 0.5)) : 1.0;
    float hitAge = incoming.w;
    vec3 hitDir = incoming.xyz;
    bool directed = length(hitDir) > 0.1;
    vec3 normHit = directed ? normalize(hitDir) : vec3(0.0, 0.0, 1.0);

    // 1. Golden ground anchor ring + bright white-gold ripple when absorbing an incoming attack.
    float lockRingAlpha = smoothstep(0.06, 0.25, age) * (1.0 - release) * alpha * interior;
    goldenFloorRing(ray, sceneDistance + 0.35, feet + vec3(0.0, 0.04, 0.0), 0.92 + 0.025 * sin(age * 2.6), 0.11,
                    lockRingAlpha * (0.42 + params.w * 0.45), mix(BASTION_GOLD, BASTION_PALE, 0.45), light);
    if (hitAge >= 0.0 && hitAge < 0.44) {
        float hitRipple = (1.0 - smoothstep(0.04, 0.44, hitAge)) * alpha;
        goldenFloorRing(ray, sceneDistance + 0.35, feet + vec3(0.0, 0.06, 0.0), 0.80 + hitAge * 2.8, 0.14,
                        hitRipple * 0.78, BASTION_WHITE, light);
    }

    // 2. Fast analytical golden Fresnel barrier dome (zero per-pixel polytope loops!).
    float domeRadius = 1.02 + release * 0.42;
    float along = dot(ray, mid);
    vec3 perp = ray * max(along, 0.0) - mid;
    float d2 = dot(perp, perp);
    float r2 = domeRadius * domeRadius;
    if (d2 < (domeRadius + 0.35) * (domeRadius + 0.35) && along + domeRadius > 0.0) {
        float rimDist = abs(sqrt(d2) - domeRadius);
        float corona = exp(-(rimDist * rimDist) / 0.026) * (0.28 + 0.18 * params.y);
        light += mix(BASTION_GOLD, BASTION_PALE, 0.4) * corona * lockRingAlpha;

        if (d2 < r2) {
            float halfChord = sqrt(max(0.0, r2 - d2));
            float tFront = along - halfChord;
            if (tFront > 0.0 && tFront < sceneDistance) {
                vec3 p = ray * tFront - mid;
                vec3 n = normalize(p);
                float fresnel = pow(1.0 - abs(dot(n, ray)), 2.8);
                float hitFocus = directed ? pow(max(0.0, dot(n, normHit)), 2.2) : 0.45;
                float hitFlash = (hitAge >= 0.0 && hitAge < 0.42) ? exp(-hitAge * 9.0) * (0.45 + 0.85 * hitFocus) : 0.0;
                float grid = 0.78 + 0.22 * sin(p.y * 14.0 - age * 2.2);
                vec3 shieldCol = mix(BASTION_GOLD, BASTION_WHITE, clamp(fresnel * 0.55 + hitFlash * 0.65, 0.0, 1.0));
                light += shieldCol * (0.045 + 0.38 * fresnel + hitFlash * 0.48) * grid * lockRingAlpha;
                if (hitFlash > 0.05) {
                    vec2 dir2 = length(perp.xy) > 0.0001 ? normalize(perp.xy) : vec2(0.0);
                    distortion += dir2 * fresnel * hitFlash * 0.22 * alpha;
                }
            }
        }
    }

    // 3. Six clean golden energy rib streaks framing the 3D voxel bastion shields.
    float lockStrut = smoothstep(0.10, 0.26, age) * (1.0 - release) * alpha * interior;
    if (lockStrut > 0.01) {
        for (int k = 0; k < 6; k++) {
            float a = float(k) * 1.04719755 + sin(age * 0.75) * 0.04;
            vec3 radial = vec3(cos(a), 0.0, sin(a));
            vec3 lower = feet + radial * 0.90 + vec3(0.0, 0.12, 0.0);
            vec3 upper = feet + radial * 0.96 + vec3(0.0, height * 0.86, 0.0);
            float pulse = (hitAge >= 0.0 && hitAge < 0.4) ? exp(-hitAge * 8.0) * max(0.0, dot(radial, normHit)) : 0.0;
            ramStreak(ray, sceneDistance, lower, upper, 0.018 + pulse * 0.016,
                      lockStrut * (0.38 + pulse * 0.65), mix(BASTION_PALE, BASTION_WHITE, pulse), light);
        }
    }
}

void applyBulwarkScreenOverlay(float bulwarkStrength, float bulwarkScale, float bulwarkAge,
                               float bulwarkPulse, float bulwarkAbsorb, float aspectRatio, inout vec3 color) {
    if (bulwarkStrength <= 0.0) return;
    float s = bulwarkStrength * bulwarkScale;
    vec2 guardUv = (texCoord - 0.5) * vec2(aspectRatio, 1.0);
    float radius = length(guardUv);
    float edge = smoothstep(0.30, 1.12, radius);
    vec3 plate = mix(BASTION_GOLD, BASTION_PALE, bulwarkAbsorb * 0.5);
    color += plate * edge * (0.022 + 0.006 * sin(bulwarkAge * 2.4)) * s;
    color += BASTION_WHITE * bulwarkPulse * edge * 0.042 * s;
}
