// Class: Bastion ("Бастіон") — Skill 1: Shield Ram ("Щитовий таран")
// Lightweight post-shader glow, shockwave dome, air-lens distortion & speed streaks paired with 3D voxel ram geometry.

void shieldRam(vec3 ray, float sceneDistance, int wave,
               inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    if (!intersectsBound(ray, sceneDistance, WaveOrigins[wave].xyz, WaveOrigins[wave].w)) return;
    vec4 body = QuakeBounds[wave * 8];
    vec4 impact = QuakeBounds[wave * 8 + 1];
    vec4 victim = QuakeBounds[wave * 8 + 2];
    vec3 rawDir = QuakeMaterials[wave * 32].xyz;
    float shield = QuakeMaterials[wave * 32].w;
    float age = WaveStyles[wave].y;
    vec4 contact = QuakeMaterials[wave * 32 + 1]; // height, confirmed, flash, suppression
    vec4 inertia = QuakeMaterials[wave * 32 + 2];
    if (body.w <= 0.0 || length(rawDir) < 0.0001) return;

    vec3 dir = normalize(rawDir);
    vec3 bodyCentre = body.xyz + vec3(0.0, body.w * 0.5, 0.0);
    vec3 side = normalize(cross(vec3(0, 1, 0), dir));
    vec3 up = vec3(0, 1, 0);
    float confirmed = contact.y;
    float rupture = confirmed * smoothstep(0.10, 0.22, age);
    float fade = 1.0 - smoothstep(0.28, 0.48, age);
    float crown = smoothstep(0.005, 0.035, age) * fade;
    vec3 nose = bodyCentre + dir * (0.92 + shield * 0.12) + inertia.xyz * 0.55;
    nose = mix(nose, impact.xyz, confirmed * smoothstep(0.055, 0.10, age));

    // 1. Nose kinetic lens + sleek golden chevron streaks around the 3D voxel shield.
    if (crown > 0.001 && intersectsBound(ray, sceneDistance, nose, 2.2)) {
        float widthBoost = 1.0 + shield * 0.35;
        vec3 prowTip = nose + dir * 0.24;
        ramStreak(ray, sceneDistance, prowTip, nose - dir * 0.42 + side * 0.72,
                  0.028 * widthBoost, crown * 0.78, BASTION_PALE, light);
        ramStreak(ray, sceneDistance, prowTip, nose - dir * 0.42 - side * 0.72,
                  0.028 * widthBoost, crown * 0.78, BASTION_PALE, light);
        ramStreak(ray, sceneDistance, nose - up * 0.38, nose + up * 0.44,
                  0.030 * widthBoost, crown * (0.55 + contact.z), BASTION_WHITE, light);

        float along = dot(ray, nose);
        if (along > 0.0 && along < sceneDistance) {
            float d = length(ray * along - nose);
            float coreGlow = exp(-d * d / 0.18) * crown * (0.35 + 0.35 * (1.0 - rupture));
            light += mix(BASTION_GOLD, BASTION_WHITE, 0.45) * coreGlow;
            float lens = exp(-d * d * 10.0) * crown * (1.0 - smoothstep(0.10, 0.20, age));
            vec2 projected = texCoord - screenPosition(nose);
            distortion -= projected / max(length(projected), 0.001) * lens * 0.26;
        }
    }

    // 2. Fast golden motion-history ribbons & light dust puffs behind the dash.
    float trail = smoothstep(0.02, 0.07, age) * (1.0 - smoothstep(0.30, 0.54, age));
    vec3 oldest = QuakeBounds[wave * 8 + 7].xyz;
    vec3 wakeCentre = (oldest + body.xyz) * 0.5 + up * 0.75;
    float wakeRadius = distance(oldest, body.xyz) * 0.5 + 1.5;
    if (trail > 0.001 && intersectsBound(ray, sceneDistance, wakeCentre, wakeRadius)) {
        for (int wing = -1; wing <= 1; wing += 2) {
            vec3 offset = side * (float(wing) * 0.54) + up * 0.85;
            vec3 previous = body.xyz + offset;
            for (int j = 0; j < 4; j++) {
                vec3 next = QuakeBounds[wave * 8 + 3 + j].xyz + offset;
                float tail = 1.0 - float(j) / 4.0;
                if (distance(previous, next) > 0.02) {
                    ramStreak(ray, sceneDistance, previous, next, 0.022 + tail * 0.018,
                              trail * tail * (0.52 + shield * 0.20), mix(BASTION_GOLD, BASTION_PALE, tail), light);
                }
                previous = next;
            }
        }
        dustPuff(ray, sceneDistance, QuakeBounds[wave * 8 + 3].xyz + up * 0.08, 0.18, trail * 0.26, light, darkness);
        dustPuff(ray, sceneDistance, QuakeBounds[wave * 8 + 6].xyz + up * 0.08, 0.24, trail * 0.18, light, darkness);
    }

    if (confirmed < 0.5) return;

    // 3. Impact corona flash, golden shock dome, floor shockwave ring & distortion.
    float t = age - 0.10;
    if (t >= 0.0 && t < 0.55 && intersectsBound(ray, sceneDistance, impact.xyz, 2.6)) {
        float flash = contact.z;
        float along = dot(ray, impact.xyz);
        if (along > 0.0 && along < sceneDistance + 0.15) {
            vec3 delta = ray * along - impact.xyz;
            float glow = exp(-dot(delta, delta) / (0.045 + t * 0.45)) * flash;
            light += (BASTION_WHITE * 1.35 + BASTION_GOLD * 0.55) * glow * (1.0 + shield * 0.40);
            vec2 projected = texCoord - screenPosition(impact.xyz);
            distortion += projected / max(length(projected), 0.001) * glow * 0.60;

            // Smooth expanding golden pressure sphere shell on impact
            float shellT = clamp(t / 0.30, 0.0, 1.0);
            float shellR = 0.28 + (1.0 - pow(1.0 - shellT, 2.5)) * (1.55 + shield * 0.35);
            float d = length(delta);
            float rim = exp(-pow((d - shellR) / 0.12, 2.0)) * (1.0 - shellT);
            light += mix(BASTION_GOLD, BASTION_WHITE, 0.45) * rim * (0.75 + shield * 0.25);
        }
        if (t < 0.36) {
            float ringEnv = (1.0 - smoothstep(0.08, 0.36, t)) * (0.72 + shield * 0.35);
            goldenFloorRing(ray, sceneDistance + 0.35, vec3(impact.x, victim.y + 0.06, impact.z),
                            0.25 + (1.0 - exp(-t * 11.0)) * 1.65, 0.12, ringEnv * 0.72, BASTION_PALE, light);
        }
    }

    // 4. Lightweight golden suppression aura around the stunned victim.
    if (contact.w > 0.001 && intersectsBound(ray, sceneDistance, victim.xyz + up * victim.w * 0.5, victim.w * 0.65 + 0.9)) {
        goldenFloorRing(ray, sceneDistance + 0.35, victim.xyz + up * 0.05, 0.62, 0.10,
                        contact.w * 0.55, BASTION_GOLD, light);
    }
}
