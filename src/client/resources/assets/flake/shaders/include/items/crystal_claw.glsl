// Active Item: Crystal Claw ("Кристалевий кіготь") — frosted crystal mist + 12 orbiting ice shards

// Exact elongated ice shard: rotated-box intersection with a frosty rim and cold inner glow.
void iceShard(vec3 ray, float sceneDistance, vec3 centre, vec3 halfSize, float angle, float alpha,
              inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor) {
    float reach = max(halfSize.x, max(halfSize.y, halfSize.z));
    if (!intersectsBound(ray, sceneDistance, centre, reach * 3.0)) return;
    float c = cos(angle), s = sin(angle), ct = cos(angle * 0.63), st = sin(angle * 0.63);
    mat3 rotation = mat3(c, s, 0.0, -s * ct, c * ct, st, s * st, -c * st, ct);
    vec3 ro = transpose(rotation) * -centre, rd = transpose(rotation) * ray;
    vec3 safeRay = mix(vec3(-1.0), vec3(1.0), step(vec3(0.0), rd)) * max(abs(rd), vec3(0.00001));
    vec3 a = (-halfSize - ro) / safeRay, b = (halfSize - ro) / safeRay;
    vec3 nearT = min(a, b), farT = max(a, b);
    float hit = max(max(nearT.x, nearT.y), nearT.z), exit = min(min(farT.x, farT.y), farT.z);
    if (hit <= 0.0 || exit < hit || hit > sceneDistance) return;
    vec3 p = ro + rd * hit;
    vec3 q = abs(p) / halfSize;
    float edgeMetric = max(min(q.x, q.y), min(max(q.x, q.y), q.z));
    float edgeGlow = smoothstep(0.86, 0.995, edgeMetric);
    vec3 face = step(halfSize * 0.99, abs(p)) * sign(p);
    vec3 normal = normalize(rotation * (face / halfSize));
    float rim = pow(1.0 - abs(dot(normal, ray)), 2.0);
    vec3 shardCol = mix(CRYSTAL_DEEP, CRYSTAL_BRIGHT, edgeGlow * 0.8 + rim * 0.5);
    chipColor += shardCol * alpha;
    chipAlpha += alpha * 0.92;
    light += CRYSTAL_BRIGHT * (edgeGlow * 0.55 + rim * 0.35) * alpha;
    darkness += (1.0 - edgeGlow * 0.7) * alpha * 0.16;
}

// Frosted crystal mist + 12 orbiting elongated ice shards around the character.
void crystalAura(vec3 ray, float sceneDistance, int wave,
                 inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor) {
    vec4 body = QuakeBounds[wave * 8];
    if (body.w <= 0.0) return;
    vec3 feet = body.xyz;
    float height = body.w;
    float alpha = WaveStyles[wave].x;
    float age = WaveStyles[wave].y;
    vec3 mid = feet + vec3(0.0, height * 0.55, 0.0);
    if (!intersectsBound(ray, sceneDistance, mid, 1.55)) return;

    // Cold vertical haze, brightest around the torso, with slow caustic drift.
    vec3 a = feet + vec3(0.0, 0.12, 0.0);
    vec3 seg = vec3(0.0, max(0.4, height - 0.18), 0.0);
    float bb = dot(ray, seg), d = dot(ray, a), e = dot(seg, a);
    float along = clamp((bb * d - e) / max(0.000001, dot(seg, seg) - bb * bb), 0.0, 1.0);
    vec3 pt = a + seg * along;
    float mistHit = dot(pt, ray);
    if (mistHit > 0.0 && mistHit < sceneDistance + 0.16) {
        vec3 sep = ray * mistHit - pt;
        float r = length(sep);
        if (r < 0.86) {
            float az = atan(sep.z, sep.x);
            float h = along * seg.y;
            float caustic = 0.5 + 0.5 * sin(h * 6.4 - age * 3.6 + sin(az * 3.0 + age * 1.7) * 1.4);
            float shell = smoothstep(0.12, 0.30, r) * (1.0 - smoothstep(0.42, 0.86, r));
            float core = exp(-r * r / 0.24) * 0.38;
            float ends = smoothstep(0.0, 0.14, along) * (1.0 - smoothstep(0.80, 1.0, along));
            float mist = (shell * caustic + core) * ends * alpha;
            light += (CRYSTAL_DEEP * 0.55 + CRYSTAL_BRIGHT * 0.45 * caustic) * mist;
            darkness += shell * ends * alpha * 0.06;
        }
    }

    // Orbiting shards: bobbing, slowly tumbling, fading in and out on their own cycles.
    int shardCount = DepthConfig.z < 0.5 ? 6 : (DepthConfig.z < 1.5 ? 9 : 12);
    for (int k = 0; k < 12; k++) {
        if (k >= shardCount) break;
        float fk = float(k);
        float h1 = fract(fk * 0.618034 + 0.21);
        float h2 = fract(fk * 0.381966 + 0.63);
        float phase = fract(age * (0.45 + 0.35 * h1) + fk / 12.0);
        float env = sin(phase * 3.14159);
        float ang = fk * 2.39996 + age * (0.55 + 0.5 * (h2 - 0.5));
        float rad = 0.42 + 0.16 * h2 + 0.05 * sin(phase * 6.28318 + fk);
        float y = 0.12 + height * (0.12 + 0.68 * h1) + 0.14 * sin(phase * 6.28318 + fk * 1.9);
        vec3 cPos = feet + vec3(cos(ang) * rad, y, sin(ang) * rad);
        float swell = 0.55 + 0.45 * env;
        vec3 halfSize = vec3(0.026 + 0.012 * h1, 0.072 + 0.055 * h2, 0.026 + 0.012 * h2) * swell;
        iceShard(ray, sceneDistance, cPos, halfSize, age * (1.1 + 1.3 * h2) + fk * 1.7,
                 alpha * env, light, darkness, chipAlpha, chipColor);
    }
}
