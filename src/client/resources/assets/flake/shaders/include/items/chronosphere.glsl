// Active Item: Chrono Trident ("Хроносфера") — Dota 2 purple Chronosphere fused with Concussed Gravitational Dissonance.

const vec3 CHRONO_DEEP = vec3(0.20, 0.03, 0.44);
const vec3 CHRONO_PURPLE = vec3(0.58, 0.14, 0.96);
const vec3 CHRONO_NEON = vec3(0.86, 0.44, 1.00);
const vec3 CHRONO_CORE = vec3(0.96, 0.82, 1.00);

void chronoShell(vec3 ray, float sceneDistance, vec3 origin, float radius, float alpha,
                 float age, float detail, float collapse,
                 inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    vec3 axes = vec3(1.0 + 0.022 * sin(age * 4.5),
                     1.0 + 0.032 * cos(age * 4.0 - 0.7), 1.0);
    vec3 r = ray / axes;
    vec3 o = origin / axes;
    float a = dot(r, r);
    float along = dot(r, o);
    float discriminant = along * along - a * (dot(o, o) - radius * radius);
    if (discriminant <= 0.0) return;

    float root = sqrt(discriminant);
    float front = (along - root) / a;
    float back = (along + root) / a;
    float entry = max(front, 0.0);
    float exit = min(back, sceneDistance);
    if (exit <= entry) return;

    bool inside = front <= 0.0;
    float hit = inside ? back : front;
    float visible = smoothstep(-0.025, 0.055, sceneDistance - hit);
    vec3 point = ray * hit - origin;
    vec3 normal = normalize(point / (axes * axes));
    float facing = abs(dot(normal, ray));
    float fresnel = pow(1.0 - facing, 2.7) * visible;
    float border = exp(-facing * facing * 11.5) * visible;

    // Gravitational caustics + subtle cosmic chrono spirals + 12 clock-face meridian ticks
    float flow = sin(normal.x * 16.0 + normal.z * 11.0 - age * 4.2)
               * sin(normal.y * 19.0 - normal.x * 8.0 + age * 2.8);
    float az = atan(normal.z, normal.x);
    float spiral = pow(max(0.0, sin(az * 3.0 + normal.y * 10.0 - age * 3.4)), 10.0) * visible * 0.45 * detail;
    float clockTicks = pow(max(0.0, cos(az * 12.0 + age * 0.35)), 18.0) * exp(-pow(normal.y / 0.14, 2.0)) * visible * detail;
    float equator = exp(-pow(normal.y / 0.032, 2.0)) * 0.32 * visible * detail;
    float floorRing = exp(-pow((point.y + 0.86) / 0.08, 2.0)) * visible * detail;

    float density = 1.0 - exp(-(exit - entry) * 0.24);
    float diffuse = 0.5 + 0.5 * dot(normal, normalize(vec3(-0.35, 0.85, 0.3)));
    float nacre = 0.5 + 0.5 * sin(facing * 10.0 + flow * 0.9 - age * 2.4);
    vec3 cosmic = mix(CHRONO_PURPLE, CHRONO_NEON, nacre * 0.65 + diffuse * 0.25);

    // Keep first-person combat inside the Chronosphere clear while giving the outer dome rich purple depth.
    float interiorScale = inside ? 0.18 : 1.0;
    float purpleTint = alpha * (fresnel * 0.28 + density * 0.16 * interiorScale + 0.07 * interiorScale) * visible;
    darkness += alpha * (0.20 * interiorScale + density * 0.14 * interiorScale) * visible;
    chipColor += mix(CHRONO_DEEP, CHRONO_PURPLE, 0.45) * purpleTint;
    chipAlpha += purpleTint * 0.72;

    float viewScale = inside ? 0.58 : 1.0;
    light += alpha * viewScale * (
        cosmic * (fresnel * 0.62 + (0.04 + 0.03 * flow) * visible * interiorScale + diffuse * density * 0.14)
        + CHRONO_CORE * border * 1.05
        + CHRONO_NEON * (floorRing * 0.72 + equator + spiral)
        + CHRONO_CORE * clockTicks * 0.52
    );

    // Gravitational refraction along the dome rim
    vec2 direction = screenPosition(origin + normal * radius) - screenPosition(origin);
    float directionLength = length(direction);
    direction = directionLength > 0.0001 ? direction / directionLength : vec2(0.0);
    distortion += direction * (fresnel * 0.85 + border * 0.35 + collapse * 0.25) * alpha;
}

void chronoCorona(vec3 ray, float sceneDistance, vec3 origin, float radius, float alpha, inout vec3 light) {
    float along = dot(ray, origin);
    if (along <= 0.0 || length(origin) <= radius) return;
    float impact = length(origin - ray * along);
    float width = 0.08 + radius * 0.05;
    float halo = exp(-pow((impact - radius) / width, 2.0));
    float front = along - sqrt(max(0.0, radius * radius - impact * impact));
    halo *= smoothstep(-0.02, 0.04, sceneDistance - front);
    light += CHRONO_NEON * halo * alpha * 0.36;
}

void chronoFragments(vec3 ray, float sceneDistance, int wave, inout vec3 light) {
    for (int i = 0; i < 12; i++) {
        if (i >= int(DepthConfig.w)) break;
        int index = wave * 32 + i;
        vec3 start = MoteOrigins[index].xyz;
        vec3 segment = MoteTails[index].xyz;
        float alpha = MoteTails[index].w;
        if (alpha <= 0.0) continue;
        float bound = length(segment) + MoteOrigins[index].w * 3.0;
        if (!intersectsBound(ray, sceneDistance, start, bound)) continue;
        float b = dot(ray, segment);
        float d = dot(ray, start);
        float e = dot(segment, start);
        float along = clamp((b * d - e) / max(0.00001, dot(segment, segment) - b * b), 0.0, 1.0);
        vec3 point = start + segment * along;
        float hit = dot(point, ray);
        if (hit <= 0.0 || hit > sceneDistance) continue;
        vec3 separation = ray * hit - point;
        float width = MoteOrigins[index].w;
        float d2 = dot(separation, separation) / (width * width);
        float streak = exp(-d2);
        light += mix(CHRONO_PURPLE, CHRONO_CORE, along) * streak * alpha * 0.78;
    }
}

void chronoSingularity(vec3 ray, float sceneDistance, vec3 origin, float collapse, float alpha,
                       inout vec3 light, inout vec2 distortion) {
    if (collapse <= 0.001) return;
    float along = dot(ray, origin);
    if (along <= 0.0 || along > sceneDistance + 0.05) return;
    float impact = length(ray * along - origin);
    float lens = exp(-impact * impact * 4.2) * collapse * alpha;
    vec2 radial = texCoord - screenPosition(origin);
    distortion -= radial / max(0.001, length(radial)) * lens * 2.4;
    float flare = exp(-pow(impact / 0.14, 2.0)) * collapse;
    light += CHRONO_CORE * flare * alpha * 0.95;
}

void chronosphereWave(vec3 ray, float sceneDistance, vec3 scenePoint, float depth, int wave,
                      inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    float alpha = WaveStyles[wave].x;
    vec3 origin = WaveOrigins[wave].xyz;
    float radius = WaveOrigins[wave].w;
    float age = WaveStyles[wave].y;
    float collapse = WaveStyles[wave].w;

    if (alpha < 0.005 || radius <= 0.01) return;
    if (!intersectsBound(ray, sceneDistance, origin, radius * 1.2 + 0.4)) return;

    chronoFragments(ray, sceneDistance, wave, light);
    chronoShell(ray, sceneDistance, origin, radius, alpha, age, 1.0, collapse, light, darkness, chipAlpha, chipColor, distortion);
    chronoCorona(ray, sceneDistance, origin, radius, alpha, light);

    // Subtle inner harmonic resonance shell (like Gravitational Dissonance echo)
    float innerScale = 0.86 + 0.02 * sin(age * 5.0);
    float dummyDark = 0.0;
    chronoShell(ray, sceneDistance, origin, radius * innerScale, alpha * 0.24, age * 1.15, 0.0, collapse,
                light, dummyDark, chipAlpha, chipColor, distortion);

    // Ground ring & world-geometry intersection contact trace
    goldenFloorRing(ray, sceneDistance, origin - vec3(0.0, 0.86, 0.0), radius, 0.095, alpha * 0.65, CHRONO_NEON, light);
    goldenFloorRing(ray, sceneDistance, origin - vec3(0.0, 0.86, 0.0), radius * 0.72, 0.045, alpha * 0.28, CHRONO_PURPLE, light);
    if (depth > 0.000001) {
        vec3 p = scenePoint - origin;
        float band = exp(-pow((length(p) - radius) / 0.09, 2.0));
        light += CHRONO_NEON * band * alpha * 0.42;
    }

    chronoSingularity(ray, sceneDistance, origin, collapse, alpha, light, distortion);
}
