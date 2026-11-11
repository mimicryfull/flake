// Class: Bastion ("Бастіон") — Skill 1: Shield Ram ("Щитовий таран")

// Shield Ram: interlocking chevrons, motion-history ribbons, then a compact fractured pressure crown.
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
    vec3 side = normalize(cross(vec3(0,1,0), dir));
    vec3 up = vec3(0,1,0);
    float confirmed = contact.y;
    float rupture = confirmed * smoothstep(0.10, 0.20, age);
    float fade = 1.0 - smoothstep(0.29, 0.48, age);
    float crown = smoothstep(0.005, 0.035, age) * fade;
    vec3 nose = bodyCentre + dir * (0.88 + shield * 0.12) + inertia.xyz * 0.65;
    // On a confirmed hit the assembled structure compresses into the fixed contact, never chases knockback.
    nose = mix(nose, impact.xyz, confirmed * smoothstep(0.055, 0.10, age));
    if (crown > 0.001 && intersectsBound(ray, sceneDistance, nose, 2.0)) {
        float sqAge = (age - 0.10) / 0.025;
        float squeeze = 1.0 - confirmed * 0.32 * exp(-sqAge * sqAge);
        for (int k = 0; k < 6; k++) {
            float f = float(k), wing = mod(f, 2.0) * 2.0 - 1.0, tier = floor(f / 2.0);
            float spring = bastionSpring((age - f * 0.006) / 0.10);
            float load = sin(age * 88.0 - f * 2.3) * 0.012 * inertia.w;
            vec3 offset = side * wing * (0.25 + (1.0 - spring) * 0.85 + rupture * (0.55 + tier * 0.17))
                        + up * ((tier - 1.0) * 0.43 * squeeze + (1.0 - spring) * (tier - 0.5) * 0.8)
                        - dir * (0.18 + tier * 0.12 + abs(1.0 - spring) * 0.45 + rupture * 0.2);
            vec3 normal = normalize(dir + side * wing * (0.75 + (1.0 - spring) * 0.9 + load));
            vec3 p = nose + offset + up * load;
            vec3 ext = vec3(0.37 + shield * 0.035, 0.30 * squeeze, 0.085 + shield * 0.035);
            ext.y *= 1.0 + (1.0 - spring) * 0.4;
            bastionPlate(ray, sceneDistance, p, bastionBasis(normal, wing * (0.10 + (1.0 - spring) * 0.8)),
                         ext, crown * (1.0 + shield * 0.25), age, f * 1.7, contact.z * 0.60,
                         max(rupture * 0.8, smoothstep(0.30, 0.48, age)), light, darkness);
        }
        ramStreak(ray, sceneDistance, nose - dir * 0.28 - up * 0.35, nose + up * 0.38,
                  0.030 + shield * 0.014, crown * (0.52 + contact.z), BASTION_WHITE, light);
        float along = dot(ray, nose);
        if (along > 0.0 && along < sceneDistance) {
            float d = length(ray * along - nose);
            float lens = exp(-d * d * 12.0) * crown * (1.0 - smoothstep(0.10, 0.18, age));
            vec2 projected = texCoord - screenPosition(nose);
            distortion -= projected / max(length(projected), 0.001) * lens * 0.24;
        }
    }

    // Actual world-space breadcrumbs; three swept ribs retain body volume from side/rear views.
    float trail = smoothstep(0.025, 0.075, age) * (1.0 - smoothstep(0.32, 0.55, age));
    vec3 oldest = QuakeBounds[wave * 8 + 7].xyz;
    vec3 wakeCentre = (oldest + body.xyz) * 0.5 + up * 0.7;
    float wakeRadius = distance(oldest, body.xyz) * 0.5 + 1.5;
    if (trail > 0.001 && intersectsBound(ray, sceneDistance, wakeCentre, wakeRadius)) {
        for (int rib = 0; rib < 3; rib++) {
            float r = float(rib) - 1.0;
            vec3 offset = side * r * 0.46 + up * (0.65 + (1.0 - abs(r)) * 0.52);
            vec3 previous = body.xyz + offset;
            for (int j = 0; j < 5; j++) {
                vec3 next = QuakeBounds[wave * 8 + 3 + j].xyz + offset;
                float tail = 1.0 - float(j) / 5.0;
                if (distance(previous, next) > 0.018)
                    ramStreak(ray, sceneDistance, previous, next, 0.024 + tail * 0.022,
                              trail * tail * (0.48 + shield * 0.18), mix(BASTION_GOLD, BASTION_PALE, tail), light);
                previous = next;
            }
        }
        for (int k = 0; k < 3; k++) {
            vec3 foot = QuakeBounds[wave * 8 + 3 + k * 2].xyz + up * 0.07;
            dustPuff(ray, sceneDistance, foot, 0.13 + float(k) * 0.035, trail * 0.24, light, darkness);
        }
        // A few armour slivers peel off the loaded edges, tumble, then lag behind the moving body.
        int slivers = DepthConfig.z < 0.5 ? 2 : 4;
        for (int k = 0; k < 4; k++) {
            if (k >= slivers) break;
            float f = float(k), local = age - 0.045 - f * 0.043;
            if (local <= 0.0 || local > 0.23) continue;
            float wing = mod(f, 2.0) * 2.0 - 1.0;
            vec3 root = QuakeBounds[wave * 8 + 3 + k].xyz + up * (0.72 + mod(f, 3.0) * 0.18);
            vec3 p = root + side * wing * (0.48 + local * 1.2) - dir * local * 0.7 - up * local * local * 2.0;
            float shed = sin(local / 0.23 * 3.14159) * trail;
            bastionPlate(ray, sceneDistance, p, bastionBasis(normalize(dir + side * wing), wing * (0.4 + local * 7.0)),
                         vec3(0.04,0.105,0.018), shed * 0.8, age, f, 0.0, local / 0.23, light, darkness);
            ramStreak(ray, sceneDistance, p, p + dir * 0.12, 0.012, shed * 0.4, BASTION_GOLD, light);
        }
    }

    if (confirmed < 0.5) return; // A miss only sheds and dissolves its wedge. No explosion or suppression.
    float t = age - 0.10;
    if (t >= 0.0 && t < 0.55 && intersectsBound(ray, sceneDistance, impact.xyz, 2.3)) {
        float flash = contact.z;
        float along = dot(ray, impact.xyz);
        if (along > 0.0 && along < sceneDistance) {
            vec3 delta = ray * along - impact.xyz;
            float glow = exp(-dot(delta, delta) / (0.035 + t * 0.5)) * flash;
            light += (BASTION_WHITE * 1.55 + BASTION_GOLD * 0.42) * glow * (1.0 + shield * 0.45);
            vec2 projected = texCoord - screenPosition(impact.xyz);
            distortion += projected / max(length(projected), 0.001) * glow * 0.65;
        }
        // Golden ground shock-ring radiating from the confirmed impact footpoint
        if (t < 0.34) {
            float ringEnv = (1.0 - smoothstep(0.08, 0.34, t)) * (0.65 + shield * 0.35);
            goldenFloorRing(ray, sceneDistance, vec3(impact.x, victim.y + 0.05, impact.z),
                            0.25 + (1.0 - exp(-t * 11.0)) * 1.55, 0.075, ringEnv * 0.65, BASTION_PALE, light);
        }
        // Six forward-canted facets form a brief 3D pressure front, NOT a flat expanding ring.
        float reach = 1.0 - exp(-t * 13.0);
        float pressure = (1.0 - smoothstep(0.07, 0.28, t)) * smoothstep(0.0, 0.012, t);
        for (int k = 0; k < 6; k++) {
            float f = float(k), angle = f * 1.04719755 + 0.25;
            vec3 radial = side * cos(angle) + up * sin(angle);
            vec3 p = impact.xyz + radial * (0.18 + reach * 0.95) + dir * reach * 0.38;
            vec3 normal = normalize(dir * 0.55 + radial);
            bastionPlate(ray, sceneDistance, p, bastionBasis(normal, angle * 0.15),
                         vec3(0.22 + reach * 0.08, 0.32, 0.035), pressure * 0.76,
                         age, f, flash * 0.40, reach * 0.45, light, darkness);
        }
        int count = DepthConfig.z < 0.5 ? 4 : (DepthConfig.z < 1.5 ? 6 : 8);
        for (int k = 0; k < 8; k++) {
            if (k >= count) break;
            float f = float(k), seed = fract(f * 0.618034 + 0.23);
            float a = f * 2.399963;
            vec3 velocity = (side * cos(a) + up * sin(a)) * (2.0 + seed * 2.2) + dir * (1.0 + seed * 2.0);
            float drag = (1.0 - exp(-t * 4.5)) / 4.5;
            vec3 p = impact.xyz + velocity * drag - up * t * t * 2.5;
            float life = (1.0 - smoothstep(0.16 + seed * 0.1, 0.42 + seed * 0.1, t));
            mat3 basis = bastionBasis(normalize(dir + side * sin(a + t * 9.0)), a + t * (3.0 + seed * 4.0));
            bastionPlate(ray, sceneDistance, p, basis, vec3(0.065, 0.12 + seed * 0.07, 0.027),
                         life, age, f, flash * 0.32, 1.0 - life, light, darkness);
            vec3 tail = p - velocity * exp(-t * 4.5) * 0.045;
            ramStreak(ray, sceneDistance, p, tail, 0.014, life * 0.68, BASTION_GOLD, light);
        }
    }
    if (contact.w > 0.001 && intersectsBound(ray, sceneDistance, victim.xyz + up * victim.w * 0.5, victim.w * 0.65 + 0.85)) {
        for (int k = 0; k < 4; k++) {
            float f = float(k), a = f * 1.5707963 + 0.45;
            vec3 radial = vec3(cos(a), 0, sin(a));
            float clampIn = bastionSpring((age - 0.12 - f * 0.012) / 0.20);
            vec3 p = victim.xyz + up * victim.w * (0.43 + mod(f, 2.0) * 0.2)
                   + radial * (0.42 + (1.0 - clampIn) * 0.38);
            bastionPlate(ray, sceneDistance, p, bastionBasis(radial, (f - 1.5) * 0.16),
                         vec3(0.15, victim.w * 0.18, 0.035), contact.w * 0.58,
                         age, f, exp(-max(0.0, age - 0.14) * 15.0) * 0.35,
                         smoothstep(0.58, 1.08, age), light, darkness);
        }
    }
}
