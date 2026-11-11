// Active Item: Matadora ("Матадора") — 3D rotating dark-void cubes + violet mist aura

// Exact 3D rotating dark-void cube with crisp violet rim and stronger outer ember glow.
void voidCube(vec3 ray, float sceneDistance, vec3 centre, float size, float angle, float alpha,
              inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor) {
    if (!intersectsBound(ray, sceneDistance, centre, size * 2.9)) return;
    float along = dot(ray, centre);
    if (along <= 0.0 || along > sceneDistance + size) return;
    // Stronger violet ember halo radiating around each rising cube
    float dist = length(ray * along - centre);
    float ember = exp(-dist * dist / (size * size * 4.6)) * alpha;
    light += (VOID_VIOLET * 0.68 + VOID_BRIGHT * 0.26 * ember) * ember;

    if (dist > size * 1.75) return;
    float c = cos(angle), s = sin(angle), c2 = cos(angle * 0.71), s2 = sin(angle * 0.71);
    mat3 rotation = mat3(c, s, 0.0, -s * c2, c * c2, s2, s * s2, -c * s2, c2);
    vec3 ro = transpose(rotation) * -centre, rd = transpose(rotation) * ray;
    vec3 safeRay = mix(vec3(-1.0), vec3(1.0), step(vec3(0.0), rd)) * max(abs(rd), vec3(0.00001));
    vec3 a = (-vec3(size) - ro) / safeRay, b = (vec3(size) - ro) / safeRay;
    vec3 nearT = min(a, b), farT = max(a, b);
    float hit = max(max(nearT.x, nearT.y), nearT.z), exit = min(min(farT.x, farT.y), farT.z);
    if (hit <= 0.0 || exit < hit || hit > sceneDistance) return;
    vec3 p = ro + rd * hit;
    vec3 q = abs(p) / max(size, 0.0001);
    float edgeMetric = max(min(q.x, q.y), min(max(q.x, q.y), q.z));
    float edgeGlow = smoothstep(0.80, 0.985, edgeMetric);
    vec3 face = step(vec3(size * 0.992), abs(p)) * sign(p);
    vec3 normal = normalize(rotation * face);
    float shade = 0.25 + 0.75 * max(0.0, dot(normal, normalize(vec3(-0.35, 0.85, 0.40))));
    vec3 darkFace = vec3(0.035, 0.008, 0.075) + VOID_VIOLET * (0.10 + 0.18 * shade);
    vec3 rimCol = mix(VOID_VIOLET * 0.85, VOID_BRIGHT * 0.95, edgeGlow * 0.65);
    vec3 cubeCol = mix(darkFace, rimCol, edgeGlow * 0.82);
    chipColor += cubeCol * alpha;
    chipAlpha += alpha * 0.96;
    light += VOID_BRIGHT * edgeGlow * alpha * 0.65;
    darkness += (1.0 - edgeGlow * 0.5) * alpha * 0.48;
}

// Blazing dark-violet mist + 14 rising 3D volumetric rotating cubes around the character.
void maskAura(vec3 ray, float sceneDistance, int wave,
              inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor) {
    vec4 body = QuakeBounds[wave * 8];
    if (body.w <= 0.0) return;
    vec3 feet = body.xyz;
    float height = body.w;
    float alpha = WaveStyles[wave].x;
    float age = WaveStyles[wave].y;
    vec3 mid = feet + vec3(0.0, height * 0.58, 0.0);
    if (!intersectsBound(ray, sceneDistance, mid, 1.60)) return;

    // Stronger rising violet glow + mist around the torso (no flat floor disk)
    vec3 a = feet + vec3(0.0, 0.18, 0.0);
    vec3 seg = vec3(0.0, max(0.4, height - 0.26), 0.0);
    float bb = dot(ray, seg), d = dot(ray, a), e = dot(seg, a);
    float along = clamp((bb * d - e) / max(0.000001, dot(seg, seg) - bb * bb), 0.0, 1.0);
    vec3 pt = a + seg * along;
    float hit = dot(pt, ray);
    if (hit > 0.0 && hit < sceneDistance + 0.16) {
        vec3 sep = ray * hit - pt;
        float r = length(sep);
        if (r < 0.92) {
            float az = atan(sep.z, sep.x);
            float h = along * seg.y;
            float flame = 0.55 + 0.45 * sin(h * 5.2 - age * 6.5 + sin(az * 2.0 + age * 2.2));
            float shell = smoothstep(0.16, 0.32, r) * (1.0 - smoothstep(0.46, 0.90, r));
            float coreGlow = exp(-r * r / 0.28) * 0.45;
            float fadeEnds = smoothstep(0.0, 0.16, along) * (1.0 - smoothstep(0.82, 1.0, along));
            float mist = (shell * flame + coreGlow) * fadeEnds * alpha;
            darkness += shell * flame * fadeEnds * alpha * 0.36;
            light += (VOID_VIOLET * 0.78 + VOID_BRIGHT * 0.36 * flame) * mist;
        }
    }

    // 14 volumetric 3D cubes rising like purple flames around the body
    int cubeCount = DepthConfig.z < 0.5 ? 8 : (DepthConfig.z < 1.5 ? 11 : 14);
    for (int k = 0; k < 14; k++) {
        if (k >= cubeCount) break;
        float fk = float(k);
        float h1 = fract(fk * 0.618034 + 0.13);
        float h2 = fract(fk * 0.381966 + 0.47);
        float speed = 0.62 + 0.38 * h1;
        float phase = fract(age * speed + fk / 14.0);
        float env = sin(phase * 3.14159);
        float ang = fk * 2.39996 + age * (0.9 + 0.6 * (h2 - 0.5)) + phase * 1.4;
        float rad = (0.34 + 0.24 * h2) + 0.07 * sin(phase * 6.28318 + fk);
        float y = 0.08 + 0.32 * h1 + phase * (height * 0.92 + 0.32);
        vec3 cPos = feet + vec3(cos(ang) * rad, y, sin(ang) * rad);
        float size = (0.044 + 0.028 * h1) * (0.30 + 0.70 * env);
        float spin = age * (2.6 + 1.8 * h2) + fk * 1.7;
        voidCube(ray, sceneDistance, cPos, size, spin, alpha * env, light, darkness, chipAlpha, chipColor);
    }
}
