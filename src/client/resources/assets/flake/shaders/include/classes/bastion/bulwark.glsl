// Class: Bastion ("Бастіон") — Skill 2: Bulwark ("Нерухомий бастіон")

// Bulwark: two offset courses of armour, flying buttresses and six compact physical ground keys.
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
    vec3 mid = feet + vec3(0.0, height * 0.54, 0.0);
    if (!intersectsBound(ray, sceneDistance, mid, height * 0.65 + 1.65)) return;
    float exitAge = max(0.0, age - offAt);
    float release = smoothstep(0.0, 0.62, exitAge);
    // Inside view remains readable; world geometry, not a full-screen sphere overlay.
    float interior = owner ? 0.38 + 0.62 * smoothstep(0.15, 0.6, length(texCoord - 0.5)) : 1.0;
    float hitAge = incoming.w;
    vec3 hitDir = incoming.xyz;
    bool directed = length(hitDir) > 0.1;

    // Fortress ground lock ring + reactive golden ripple when taking a hit under Bulwark
    float lockRingAlpha = smoothstep(0.08, 0.28, age) * (1.0 - release) * alpha * interior;
    goldenFloorRing(ray, sceneDistance, feet + vec3(0.0, 0.04, 0.0), 0.72 + 0.02 * sin(age * 2.4), 0.045,
                    lockRingAlpha * (0.28 + params.w * 0.45), mix(BASTION_GOLD, BASTION_PALE, 0.4), light);
    if (hitAge >= 0.0 && hitAge < 0.42) {
        float hitRipple = (1.0 - smoothstep(0.05, 0.42, hitAge)) * alpha;
        goldenFloorRing(ray, sceneDistance, feet + vec3(0.0, 0.05, 0.0), 0.68 + hitAge * 2.4, 0.06,
                        hitRipple * 0.62, BASTION_WHITE, light);
    }

    for (int k = 0; k < 12; k++) {
        float f = float(k), course = floor(f / 6.0), sector = mod(f, 6.0);
        float seed = f * 1.913;
        float delay = sector * 0.013 + course * 0.045;
        float localAge = age - delay;
        if (localAge <= 0.0) continue;
        float spring = bastionSpring(localAge / 0.28);
        // Courses counter-drift by only a few degrees; the fortress never spins like a spell.
        float angle = sector * 1.04719755 + course * 0.5235988 + sin(age * 0.65 + course * 2.0) * 0.025;
        vec3 radial = vec3(cos(angle), 0.0, sin(angle));
        vec3 tangent = vec3(-radial.z, 0.0, radial.x);
        float angular = directed ? acos(clamp(dot(radial, normalize(hitDir + vec3(0,0.0001,0))), -1.0, 1.0)) : 0.0;
        // A local impulse arrives at adjacent panels later; damped recoil restores their exact rest pose.
        float elapsed = hitAge - angular * 0.055;
        float impulse = elapsed >= 0.0 ? exp(-elapsed * 14.0) * sin(elapsed * 37.0) : 0.0;
        float locality = directed ? exp(-angular * angular * 1.9) : 0.20;
        float excitation = params.y * locality;
        float wfDiff = (angular - hitAge * 8.0) / 0.3;
        float wavefront = elapsed > 0.0 ? exp(-wfDiff * wfDiff) * exp(-hitAge * 6.0) : 0.0;
        float loose = smoothstep(0.0, 0.48, max(0.0, exitAge - sector * 0.012 - course * 0.025));
        float breathe = sin(age * 1.6 + seed) * 0.012 + sin(age * 2.7 - seed) * 0.006;
        float radius = 0.76 + course * 0.12 + (1.0 - spring) * (0.9 + course * 0.3)
                     - impulse * locality * 0.12 + breathe + loose * loose * 0.56;
        float y = height * (0.32 + course * 0.39) + (1.0 - spring) * (course < 0.5 ? -0.28 : 0.55)
                + loose * (0.12 + 0.12 * sin(seed));
        vec3 p = feet + radial * radius + vec3(0,y,0) + tangent * (1.0 - spring) * sin(seed) * 0.32;
        float roll = (1.0 - spring) * sin(seed + 0.4) * 1.15 + breathe * 0.3 + loose * sin(seed) * 0.55;
        vec3 normal = normalize(radial + vec3(0, course < 0.5 ? -0.12 : 0.18, 0)
                                + tangent * ((1.0 - spring) * 0.8 + impulse * locality * 0.2));
        float settleDiff = (localAge - 0.23) / 0.055;
        float settle = exp(-settleDiff * settleDiff) * 0.35;
        vec3 ext = vec3(course < 0.5 ? 0.34 : 0.39, height * (course < 0.5 ? 0.27 : 0.25), course < 0.5 ? 0.085 : 0.10);
        ext.y *= 1.0 + (1.0 - spring) * 0.18;
        ext.x *= 1.0 - (1.0 - spring) * 0.12;
        float plateAlpha = alpha * interior * smoothstep(0.0, 0.04, localAge);
        bastionPlate(ray, sceneDistance, p, bastionBasis(normal, roll), ext, plateAlpha,
                     age, seed, excitation * 0.85 + wavefront * 0.25 + settle + params.w * 0.15,
                     loose, light, darkness);
        // Interior reinforcement is spatially separated from the translucent outer armour.
        if (mod(f, 2.0) < 0.5 && DepthConfig.z > 0.5) {
            vec3 inner = feet + radial * (0.55 + loose * 0.5) + vec3(0, y, 0);
            bastionPlate(ray, sceneDistance, inner, bastionBasis(normal, roll * 0.4), ext * vec3(0.20,0.87,0.55),
                         plateAlpha * 0.62, age + 0.6, seed, excitation * 0.4 + params.x * (0.015 + 0.010 * sin(age * 2.0 + seed)), loose, light, darkness);
        }
        // Follow-through only during departure: a short wake behind each independently released segment.
        if (loose > 0.02 && loose < 0.95)
            ramStreak(ray, sceneDistance, p, p - radial * loose * 0.22 - vec3(0,loose * 0.1,0),
                      0.015, plateAlpha * sin(loose * 3.14159) * 0.45, BASTION_GOLD, light);
    }
    for (int k = 0; k < 6; k++) {
        float f = float(k), a = f * 1.04719755;
        vec3 radial = vec3(cos(a),0,sin(a));
        float spring = bastionSpring((age - f * 0.012) / 0.22);
        float radius = 0.67 + (1.0 - spring) * 0.40 + release * 0.15;
        vec3 key = feet + radial * radius + vec3(0,0.065 + abs(1.0 - spring) * 0.2,0);
        mat3 basis = mat3(vec3(-radial.z,0,radial.x), radial, vec3(0,1,0));
        bastionPlate(ray, sceneDistance, key, basis, vec3(0.14,0.32,0.045), alpha * 0.72,
                     age, f, params.w * 0.28, release * 0.8, light, darkness);
        float lock = smoothstep(0.12, 0.3, age) * (1.0 - release);
        vec3 lower = feet + radial * 0.62 + vec3(0,0.14,0);
        vec3 upper = feet + radial * 0.72 + vec3(0,height * 0.7,0);
        ramStreak(ray, sceneDistance, lower, upper, 0.014, lock * alpha * interior * 0.32,
                  BASTION_PALE, light);
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
    color += plate * edge * (0.018 + 0.004 * sin(bulwarkAge * 2.4)) * s;
    color += BASTION_WHITE * bulwarkPulse * edge * 0.035 * s;
}
