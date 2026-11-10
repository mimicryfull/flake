// Class: Concussed ("Контужений") — Skill 2: Electric Quake ("Електричний землетрус")

// Closest approach to a finite 3D channel, clipped against opaque scene depth.
// Both lightning and sparks are world-space volumes, never projected ground textures.
void discharge(vec3 ray, float sceneDistance, vec3 start, vec3 end, float width, float alpha, inout vec3 light) {
    vec3 segment = end - start;
    vec3 centre = (start + end) * 0.5;
    if (!intersectsBound(ray, sceneDistance, centre, length(segment) * 0.5 + width * 3.8)) return;
    float b = dot(ray, segment), d = dot(ray, start), e = dot(segment, start);
    float along = clamp((b * d - e) / max(0.000001, dot(segment, segment) - b * b), 0.0, 1.0);
    vec3 point = start + segment * along;
    float hit = dot(point, ray);
    if (hit <= 0.0 || hit > sceneDistance + 0.008) return;
    float aa = max(width * 0.20, hit * 0.00065);
    float distanceToChannel = length(ray * hit - point);
    if (distanceToChannel > width * 3.8 + aa) return;
    float core = 1.0 - smoothstep(width * 0.21, width * 0.21 + aa, distanceToChannel);
    float sheath = exp(-distanceToChannel * distanceToChannel / (width * width * 1.15));
    float halo = exp(-distanceToChannel * distanceToChannel / (width * width * 6.8));
    light += (ELECTRIC_CORE * core * 1.38 + ELECTRIC_CYAN * sheath * 0.72 + ELECTRIC_BLUE * halo * 0.24) * alpha;
}

// Low-lying ionized electro-dust / electric fog across the ~3.7-block AoE corridor.
// Depth-tested and terrain-anchored via cached group centres; soft organic falloff, zero rectangular borders.
void electroDust(vec3 ray, float sceneDistance, vec3 scenePoint, vec3 origin, float front, float age, float yaw,
                 vec3 groupCentre, inout vec3 light, inout float tint) {
    vec2 forwardDir = vec2(cos(yaw), sin(yaw));
    vec2 rightDir = vec2(-forwardDir.y, forwardDir.x);
    float groupForward = dot(groupCentre.xz - origin.xz, forwardDir);

    // Evaluate both at the opaque terrain surface and a low-altitude slab above the ground.
    float slabY = groupCentre.y + 0.14;
    float tSlab = abs(ray.y) > 0.0005 ? (slabY / ray.y) : -1.0;
    bool validSlab = tSlab > 0.0 && tSlab < sceneDistance;
    bool validSurf = sceneDistance < 90.0 && abs(scenePoint.y - groupCentre.y) < 0.78;
    if (!validSlab && !validSurf) return;

    vec3 samplePos = validSurf ? scenePoint : ray * tSlab;
    if (validSlab && validSurf) {
        samplePos = mix(ray * tSlab, scenePoint, 0.58);
    }

    vec2 rel = samplePos.xz - origin.xz;
    float f = dot(rel, forwardDir);
    float l = dot(rel, rightDir);
    if (abs(f - groupForward) > 0.88 || f < 0.20 || f > min(front + 0.32, 9.25)) return;

    float h = samplePos.y - (groupCentre.y - 0.04);
    if (h < -0.22 || h > 0.85) return;

    float tNorm = clamp((f - 0.35) / (9.0 - 0.35), 0.0, 1.0);
    float power = tNorm * sqrt(tNorm);

    // Organic animated multi-frequency plasma waves keep edges soft and alive.
    float wispA = sin(f * 3.9 - age * 14.0 + sin(l * 3.4 + age * 6.5));
    float wispB = cos(l * 5.2 - f * 2.6 + age * 10.5);
    float micro = 0.5 + 0.5 * sin(f * 13.0 + l * 11.0 - age * 28.0);
    float noise = 0.52 + 0.28 * wispA * wispB + 0.20 * micro;

    // Soft corridor width falloff matching the ~3.7-block hitbox (HALF_WIDTH = 1.85).
    float halfWidth = 1.82 + 0.14 * wispA;
    float latRatio = abs(l) / max(0.5, halfWidth);
    if (latRatio >= 1.15) return;
    float widthMask = pow(clamp(1.0 - latRatio * latRatio, 0.0, 1.0), 1.45);

    // Longitudinal weight per spatial group so adjacent groups blend seamlessly without double-counting.
    float groupWeight = exp(-pow((f - groupForward) / 0.58, 2.0));
    float vertMask = exp(-max(0.0, h) * (3.6 - power * 1.0)) * smoothstep(-0.22, 0.02, h);

    // Progressive wavefront reveal + lingering afterglow that outlives the main lightning bolts.
    float frontEdge = smoothstep(front + 0.26, front - 0.22, f);
    float crest = exp(-pow((front - f) / 0.55, 2.0)) * (0.45 + 0.55 * power);
    float arrivalApprox = tNorm * tNorm * (3.0 - 2.0 * tNorm) * 0.50;
    float localAge = max(0.0, age - arrivalApprox);
    float linger = smoothstep(0.0, 0.035, localAge) * (1.0 - smoothstep(0.18 + power * 0.14, 0.52 + power * 0.12, localAge));

    // Escalate density & ionized scintillation from subtle in the near zone to rich in the far high-damage zone.
    float qualityScale = DepthConfig.z < 0.5 ? 0.65 : (DepthConfig.z < 1.5 ? 0.85 : 1.0);
    float density = widthMask * groupWeight * vertMask * frontEdge * linger * (0.32 + power * 0.82) * noise * qualityScale;
    if (density <= 0.001) return;

    float sparkle = pow(micro, 5.0) * (0.25 + 0.75 * power) * (DepthConfig.z > 0.5 ? 1.0 : 0.4);
    vec3 dustColor = mix(ELECTRIC_BLUE, ELECTRIC_CYAN, 0.38 + 0.42 * power + 0.20 * wispA)
                   + ELECTRIC_CORE * (crest * 0.38 + sparkle * 0.45);
    light += dustColor * density * (0.22 + crest * 0.16);
    tint += density * 0.085;
}

// Exact rotating box intersection for the few decorative ground chips (no mesh or raymarch).
void groundChip(vec3 ray, float sceneDistance, int index, inout vec3 light, inout float tint, inout vec3 chipColor) {
    vec3 centre = MoteOrigins[index].xyz;
    float size = -MoteOrigins[index].w;
    if (!intersectsBound(ray, sceneDistance, centre, size * 1.8)) return;
    float angle = QuakeMaterials[index].w;
    float c = cos(angle), s = sin(angle), c2 = cos(angle * 0.73), s2 = sin(angle * 0.73);
    mat3 rotation = mat3(c, s, 0, -s * c2, c * c2, s2, s * s2, -c * s2, c2);
    vec3 ro = transpose(rotation) * -centre, rd = transpose(rotation) * ray;
    vec3 safeRay = mix(vec3(-1.0), vec3(1.0), step(vec3(0.0), rd)) * max(abs(rd), vec3(0.00001));
    vec3 a = (-vec3(size) - ro) / safeRay, b = (vec3(size) - ro) / safeRay;
    vec3 nearT = min(a, b), farT = max(a, b);
    float hit = max(max(nearT.x, nearT.y), nearT.z), exit = min(min(farT.x, farT.y), farT.z);
    if (hit <= 0.0 || exit < hit || hit > sceneDistance) return;
    vec3 p = ro + rd * hit;
    vec3 face = step(vec3(size * 0.995), abs(p)) * sign(p);
    vec3 normal = normalize(rotation * face);
    float shade = 0.45 + max(0.0, dot(normal, normalize(vec3(-0.4, 0.9, -0.25)))) * 0.55;
    float grit = 0.88 + 0.12 * sin(p.x * 170.0 + p.y * 90.0) * sin(p.z * 140.0);
    float alpha = MoteTails[index].w;
    chipColor += QuakeMaterials[index].rgb * shade * grit * alpha;
    tint += alpha;
}

void electricQuake(vec3 ray, float sceneDistance, vec3 scenePoint, int wave,
                   inout vec3 light, inout float tint, inout float chipAlpha, inout vec3 chipColor) {
    vec3 origin = WaveOrigins[wave].xyz;
    float front = WaveOrigins[wave].w;
    float age = WaveStyles[wave].y;
    float yaw = WaveStyles[wave].w;
    // Spatial and temporal batching avoids visiting every segment for every fullscreen pixel.
    for (int group = 0; group < 8; group++) {
        vec4 bound = QuakeBounds[wave * 8 + group];
        if (bound.w <= 0.0 || !intersectsBound(ray, sceneDistance, bound.xyz, bound.w)) continue;
        electroDust(ray, sceneDistance, scenePoint, origin, front, age, yaw, bound.xyz, light, tint);
        for (int j = 0; j < 8; j++) {
            int index = wave * 64 + group * 8 + j;
            if (QuakeStarts[index].w <= 0.0 || QuakeEnds[index].w <= 0.0) continue;
            discharge(ray, sceneDistance, QuakeStarts[index].xyz, QuakeEnds[index].xyz,
                      QuakeStarts[index].w, QuakeEnds[index].w, light);
        }
        for (int j = 0; j < 4; j++) {
            int index = wave * 32 + group * 4 + j;
            if (MoteTails[index].w <= 0.0) continue;
            if (MoteOrigins[index].w < 0.0) groundChip(ray, sceneDistance, index, light, chipAlpha, chipColor);
            else discharge(ray, sceneDistance, MoteOrigins[index].xyz, MoteOrigins[index].xyz + MoteTails[index].xyz,
                           MoteOrigins[index].w, MoteTails[index].w, light);
        }
    }
}
