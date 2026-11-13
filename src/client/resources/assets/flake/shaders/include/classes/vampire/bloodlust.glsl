// Class: Vampire ("Упир") — Skill 2: Bloodlust ("Жага крові") & Blood Siphon ("Вампіризм")

void ghoulBloodlustMistAndDroplets(vec3 ray, float sceneDistance, float age, float height, vec3 bodyPos,
                                   vec4 auraState, vec4 lunge, float inside, float feeding, float heartbeat,
                                   inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor) {
    float aura = auraState.z;
    float swing = auraState.w;
    float mistAlpha = max(aura, lunge.w * 0.65) * mix(1.0, 0.35, inside);
    if (mistAlpha <= 0.005 || !intersectsBound(ray, sceneDistance, bodyPos + vec3(0.0, height * 0.55, 0.0), 1.65)) return;

    vec3 a = bodyPos + vec3(0.0, 0.14, 0.0);
    vec3 seg = vec3(0.0, height - 0.20, 0.0);
    float bb = dot(ray, seg), d = dot(ray, a), e = dot(seg, a);
    float along = clamp((bb * d - e) / max(0.000001, dot(seg, seg) - bb * bb), 0.0, 1.0);
    vec3 pt = a + seg * along;
    float mistHit = dot(pt, ray);
    if (mistHit > 0.0 && mistHit < sceneDistance + 0.16) {
        vec3 sep = ray * mistHit - pt;
        float r = length(sep);
        if (r < 0.92) {
            float az = atan(sep.z, sep.x);
            float h = along * seg.y;
            float artery = 0.55 + 0.45 * sin(h * 5.8 - age * 7.5 + sin(az * 3.0 + age * 2.6));
            float shell = smoothstep(0.14, 0.32, r) * (1.0 - smoothstep(0.45, 0.90, r));
            float coreGlow = exp(-r * r / 0.26) * (0.48 + heartbeat * 0.25 + feeding * 0.45);
            float fadeEnds = smoothstep(0.0, 0.15, along) * (1.0 - smoothstep(0.82, 1.0, along));
            float mist = (shell * artery + coreGlow) * fadeEnds * mistAlpha;
            darkness += shell * artery * fadeEnds * mistAlpha * 0.28;
            light += (GHOUL_BLOOD * 0.65 + GHOUL_RUBY * (0.48 * artery + feeding * 0.35)) * mist;
        }
    }
    // Crimson arterial floor pulse ring on activation
    if (auraState.x >= 0.0 && auraState.x < 0.45) {
        float ringEnv = (1.0 - smoothstep(0.08, 0.45, auraState.x)) * mistAlpha;
        goldenFloorRing(ray, sceneDistance, bodyPos + vec3(0.0, 0.04, 0.0),
                        0.25 + auraState.x * 2.8, 0.065, ringEnv * 0.75, GHOUL_RUBY, light);
    }
    // Orbiting & rising 3D crimson blood droplets around the Vampire
    int dropCount = DepthConfig.z < 0.5 ? 5 : (DepthConfig.z < 1.5 ? 8 : 10);
    for (int k = 0; k < 10; k++) {
        if (k >= dropCount) break;
        float fk = float(k);
        float h1 = fract(fk * 0.618034 + 0.17);
        float h2 = fract(fk * 0.381966 + 0.53);
        float speed = 0.72 + 0.42 * h1 + feeding * 0.35;
        float phase = fract(age * speed + fk / 10.0);
        float env = sin(phase * 3.14159);
        float ang = fk * 2.39996 + age * (1.35 + 0.8 * (h2 - 0.5) + swing * 1.2) + phase * 1.5;
        float rad = 0.36 + 0.24 * h2 + 0.06 * sin(phase * 6.28318 + fk);
        float y = 0.10 + 0.25 * h1 + phase * (height * 0.90 + 0.25);
        vec3 dPos = bodyPos + vec3(cos(ang) * rad, y, sin(ang) * rad);
        float sz = (0.022 + 0.015 * h1) * (0.45 + 0.55 * env) * (1.0 + feeding * 0.25);
        bloodCube(ray, sceneDistance, dPos, vec3(sz, sz * 1.45, sz), mistAlpha * env * 0.95, light, chipAlpha, chipColor);
    }
}

void ghoulBlood(vec3 ray, float sceneDistance, int wave,
                inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    if (!intersectsBound(ray, sceneDistance, WaveOrigins[wave].xyz, WaveOrigins[wave].w)) return;
    int material = wave * 32;
    vec4 body = QuakeMaterials[material];
    float age = WaveStyles[wave].y;
    vec4 auraState = QuakeMaterials[material + 2]; // auraAge, release, aura, swing
    vec4 lunge = QuakeMaterials[material + 3];     // lungeAge, confirmed, flash, compression
    vec4 contact = QuakeMaterials[material + 4];
    vec4 launch = QuakeMaterials[material + 8];    // launchX, launchY, launchZ, lungeYaw
    float inside = QuakeMaterials[material + 9].z;
    float feeding = QuakeMaterials[material + 7].w + QuakeMaterials[material + 8].w;
    float heartbeat = QuakeMaterials[material + 10].y;
    float height = max(1.2, body.w);

    // 1. Volumetric crimson torso mist + arterial floor pulse ring & orbiting 3D blood droplets during Bloodlust
    ghoulBloodlustMistAndDroplets(ray, sceneDistance, age, height, body.xyz, auraState, lunge, inside, feeding, heartbeat,
                                  light, darkness, chipAlpha, chipColor);

    // 2. Living blood ribbons (aura wings, secondary claws, lunge wake, and siphon strands)
    for (int group = 0; group < 8; group++) {
        vec4 bound = QuakeBounds[wave * 8 + group];
        if (bound.w <= 0.0 || !intersectsBound(ray, sceneDistance, bound.xyz, bound.w)) continue;
        int kind = group < 2 ? 0 : (group < 5 ? 1 : (group < 7 ? 2 : 3));
        if (group == 7 && !(lunge.y > 0.5 && lunge.x >= 0.055 && lunge.x < 0.435)) kind = 0;
        float viewScale = kind == 0 ? mix(1.0, 0.38, inside) : 1.0;
        vec3 ribbonLight = vec3(0), ribbonColor = vec3(0);
        float ribbonDark = 0.0, ribbonAlpha = 0.0;
        for (int j = 0; j < 8; j++) {
            int index = wave * 64 + group * 8 + j;
            vec4 start = QuakeStarts[index], end = QuakeEnds[index];
            if (start.w <= 0.0 || end.w <= 0.0) continue;
            int pathCount = group >= 5 ? 4 : 8;
            float seed = float(group) + (pathCount == 4 ? float(j / 4) * 0.3 : 0.0);
            int node = j % pathCount;
            vec3 before = node > 0 && QuakeStarts[index - 1].w > 0.0 ? QuakeStarts[index - 1].xyz : start.xyz * 2.0 - end.xyz;
            vec3 after = node < pathCount - 1 && QuakeStarts[index + 1].w > 0.0 ? QuakeEnds[index + 1].xyz : end.xyz * 2.0 - start.xyz;
            vec3 midpoint = (-before + start.xyz * 9.0 + end.xyz * 9.0 - after) / 16.0;
            ghoulRibbon(ray, sceneDistance, start.xyz, midpoint, start.w, end.w * viewScale,
                        age, seed, kind, body.xyz, kind == 0 ? feeding : 0.0, ribbonLight, ribbonDark, ribbonAlpha, ribbonColor);
            ghoulRibbon(ray, sceneDistance, midpoint, end.xyz, start.w * 0.96, end.w * viewScale,
                        age, seed, kind, body.xyz, kind == 0 ? feeding : 0.0, ribbonLight, ribbonDark, ribbonAlpha, ribbonColor);
        }
        light += ribbonLight; darkness += ribbonDark; chipAlpha += ribbonAlpha; chipColor += ribbonColor;
    }

    // 3. Blood Siphon extraction & ingestion knots
    for (int i = 0; i < 2; i++) {
        vec4 extraction = QuakeMaterials[material + 5 + i];
        vec4 ingestion = QuakeMaterials[material + 7 + i];
        ghoulKnot(ray, sceneDistance, extraction.xyz, vec3(0.12, 0.16, 0.11), extraction.w * 1.2, age, 0.45, light, darkness);
        ghoulKnot(ray, sceneDistance, ingestion.xyz, vec3(0.16, 0.25, 0.15), ingestion.w * 1.1, age, 0.35, light, darkness);
    }

    // 4. Blood Lunge launch shockwave, shedding wake droplets, and confirmed impact rupture
    ghoulBloodLunge(ray, sceneDistance, material, age, height, body.xyz, lunge, contact, launch,
                    light, darkness, chipAlpha, chipColor, distortion);
}

float ghoulHash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float ghoulNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(ghoulHash21(i), ghoulHash21(i + vec2(1.0, 0.0)), f.x),
               mix(ghoulHash21(i + vec2(0.0, 1.0)), ghoulHash21(i + vec2(1.0, 1.0)), f.x), f.y);
}

void applyBloodlustScreenOverlay(float ghoulStrength, float ghoulScale, float ghoulAge,
                                 float ghoulHeartbeat, float ghoulBurst, float aspectRatio, inout vec3 color) {
    if (ghoulStrength <= 0.0) return;
    float s = ghoulStrength * ghoulScale;
    vec2 uv = (texCoord - 0.5) * vec2(aspectRatio, 1.0);
    float radius = length(uv);
    float hb = ghoulHeartbeat;

    // Deep vignette that breathes with the heartbeat
    float edge = smoothstep(0.20 - hb * 0.08, 0.96, radius);

    // Blocky voxel crawl on the vignette boundary
    vec2 cell = floor(texCoord * vec2(aspectRatio, 1.0) * 44.0);
    float blocky = smoothstep(0.0, 0.14, edge * 1.40 - ghoulHash21(cell + floor(ghoulAge * 2.0)) * 0.30);

    // Capillary veins creeping deeper from the corners
    float n = ghoulNoise(uv * 5.5 + vec2(ghoulAge * 0.22, -ghoulAge * 0.15));
    float n2 = ghoulNoise(uv * 11.0 - vec2(ghoulAge * 0.10, ghoulAge * 0.18));
    float veinA = 1.0 - smoothstep(0.0, 0.055, abs(n - 0.5));
    float veinB = 1.0 - smoothstep(0.0, 0.036, abs(n2 - 0.5));
    float veins = (veinA + veinB * 0.65) * smoothstep(0.30, 0.88, radius);

    // Burst ring sweeping inward from the screen edges
    float ringR = mix(1.25, 0.10, clamp(ghoulBurst * 1.4, 0.0, 1.0));
    float ring = exp(-pow((radius - ringR) * 9.0, 2.0)) * clamp(ghoulBurst * 2.0, 0.0, 1.0);

    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(color, vec3(luma) * vec3(0.85, 0.42, 0.45), min(edge * 0.58 * s, 0.78));
    color *= 1.0 - min(edge * 0.55 * s, 0.78);

    float pulse = 0.075 + 0.055 * hb + 0.075 * ghoulBurst;
    vec3 abyss = vec3(0.16, 0.003, 0.012);
    vec3 deep = GHOUL_BLOOD;
    vec3 hot = GHOUL_RUBY;
    color += mix(abyss, mix(deep, hot, 0.30 + 0.40 * hb), blocky) * edge * pulse * s * 2.2;
    color += mix(deep, hot, 0.65) * veins * (0.09 + 0.14 * hb) * s;
    color += mix(hot, vec3(1.0, 0.35, 0.28), 0.5) * ring * 0.26 * s;
}
