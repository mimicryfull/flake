// Passive: Hunter Compass ("Компас мисливця") — through-wall crimson-gold capsule highlight

void hunterHighlight(vec3 ray, float sceneDistance, int i, inout vec3 light) {
    vec3 center = WaveOrigins[i].xyz;
    float height = max(0.6, WaveOrigins[i].w);
    float alpha = WaveStyles[i].x;
    float age = WaveStyles[i].y;
    float fade = alpha * smoothstep(0.0, 0.05, age) * (1.0 - smoothstep(0.45, 0.65, age));
    if (fade <= 0.001) return;

    float radius = 0.38;
    float halfSeg = max(0.15, height * 0.5 - radius * 0.5);
    vec3 seg = vec3(0.0, halfSeg * 2.0, 0.0);
    vec3 a = center - vec3(0.0, halfSeg, 0.0);

    float bb = dot(ray, seg);
    float d = dot(ray, a);
    float e = dot(seg, a);
    float denom = max(0.000001, dot(seg, seg) - bb * bb);
    float u = clamp((bb * d - e) / denom, 0.0, 1.0);
    vec3 pt = a + seg * u;

    // Do NOT reject when along > sceneDistance: highlight must render through walls.
    float along = dot(pt, ray);
    if (along <= 0.0) return;

    float dist = length(ray * along - pt);
    if (dist > radius * 2.2) return;

    float normDist = dist / radius;
    float rimDelta = (normDist - 0.92) / 0.24;
    float rim = exp(-(rimDelta * rimDelta));
    float fill = (1.0 - smoothstep(0.0, 1.05, normDist)) * 0.22;
    float pulse = 0.85 + 0.15 * sin(age * 10.0);
    float wallFactor = along > sceneDistance ? 0.95 : 0.35;

    vec3 crimson = vec3(0.95, 0.14, 0.16);
    vec3 gold = vec3(1.0, 0.78, 0.22);
    vec3 rimCol = mix(crimson, gold, clamp(smoothstep(0.75, 1.05, normDist) * 0.65 + 0.15 * pulse, 0.0, 1.0));
    light += rimCol * (rim * 1.15 + fill) * wallFactor * fade * pulse;
}
