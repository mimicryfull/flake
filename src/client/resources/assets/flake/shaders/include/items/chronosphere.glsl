// Active Item: Chrono Trident ("Хроносфера") — Dota 2 purple Chronosphere fused with Concussed Gravitational Dissonance.

const vec3 CHRONO_DEEP = vec3(0.20, 0.03, 0.44);
const vec3 CHRONO_PURPLE = vec3(0.58, 0.14, 0.96);
const vec3 CHRONO_NEON = vec3(0.86, 0.44, 1.00);
const vec3 CHRONO_CORE = vec3(0.96, 0.82, 1.00);

float chronoHash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float chronoNoise3D(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = chronoHash31(i + vec3(0.0, 0.0, 0.0));
    float n100 = chronoHash31(i + vec3(1.0, 0.0, 0.0));
    float n010 = chronoHash31(i + vec3(0.0, 1.0, 0.0));
    float n110 = chronoHash31(i + vec3(1.0, 1.0, 0.0));
    float n001 = chronoHash31(i + vec3(0.0, 0.0, 1.0));
    float n101 = chronoHash31(i + vec3(1.0, 0.0, 1.0));
    float n011 = chronoHash31(i + vec3(0.0, 1.0, 1.0));
    float n111 = chronoHash31(i + vec3(1.0, 1.0, 1.0));
    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);
    return mix(mix(nx00, nx10, f.y), mix(nx01, nx11, f.y), f.z);
}

float chronoFbm3D(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += amp * chronoNoise3D(p);
        p = p * 2.07 + vec3(13.1, 7.7, 19.3);
        amp *= 0.5;
    }
    return sum;
}

vec3 chronoSkyMaelstrom(vec3 ray, float age) {
    float swirl = age * 0.14 + (1.0 - abs(ray.y)) * 0.85;
    float cs = cos(swirl);
    float sn = sin(swirl);
    vec3 dir = vec3(ray.x * cs - ray.z * sn, ray.y, ray.x * sn + ray.z * cs);

    vec3 p = dir * 2.7 + vec3(0.0, age * 0.08, 0.0);
    vec3 warp1 = vec3(
        chronoFbm3D(p),
        chronoFbm3D(p + vec3(5.2, 1.3, 2.8)),
        chronoFbm3D(p + vec3(2.1, 8.4, 4.7))
    );
    vec3 warp2 = vec3(
        chronoFbm3D(p + 2.35 * warp1 + vec3(1.7, 9.2, -age * 0.12)),
        chronoFbm3D(p + 2.35 * warp1 + vec3(8.3, 2.8, age * 0.10)),
        chronoFbm3D(p + 2.35 * warp1 + vec3(4.5, 6.1, 3.2))
    );
    float density = chronoFbm3D(p + 2.65 * warp2);

    // Ridged plasma filaments & deep void pockets for an abyssal purple time-pocket sky
    float riftRaw = abs(chronoFbm3D(dir * 5.4 + warp2 * 2.2 + vec3(age * 0.09)) * 2.0 - 1.0);
    float filaments = pow(clamp(1.0 - riftRaw, 0.0, 1.0), 3.3);
    float depthMask = smoothstep(0.20, 0.84, density);
    float stars = pow(clamp(chronoNoise3D(dir * 44.0 + warp1 * 1.35), 0.0, 1.0), 14.0) * 1.45;

    vec3 abyss = vec3(0.025, 0.004, 0.068);
    vec3 nebula = vec3(0.26, 0.035, 0.56);
    vec3 violet = vec3(0.58, 0.13, 0.95);
    vec3 magentaCore = vec3(0.92, 0.50, 1.00);

    vec3 sky = mix(abyss, nebula, depthMask);
    sky = mix(sky, violet, smoothstep(0.42, 0.90, warp2.x) * 0.68);
    sky += mix(violet, magentaCore, filaments) * filaments * 0.88;
    sky += CHRONO_CORE * stars;
    return sky;
}

void chronoShell(vec3 ray, float sceneDistance, vec3 origin, float radius, float alpha,
                 float age, float detail, float collapse, bool isSky,
                 inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor,
                 inout vec2 distortion, inout vec3 skyOverride, inout float skyOverrideAlpha) {
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

    // Deep swirling purple void sky when viewing sky inside (or through) the Chronosphere dome
    if (detail > 0.5 && isSky) {
        float skyFactor = inside ? alpha * 0.96 : alpha * 0.56 * visible;
        if (skyFactor > skyOverrideAlpha) {
            skyOverride = chronoSkyMaelstrom(ray, age);
            skyOverrideAlpha = skyFactor;
        }
    }

    // Gravitational caustics + cosmic chrono spirals + 12 clock-face meridian ticks
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
                      inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor,
                      inout vec2 distortion, inout vec3 skyOverride, inout float skyOverrideAlpha) {
    float alpha = WaveStyles[wave].x;
    vec3 origin = WaveOrigins[wave].xyz;
    float radius = WaveOrigins[wave].w;
    float age = WaveStyles[wave].y;
    float collapse = WaveStyles[wave].w;
    bool isSky = depth <= 0.000001;

    if (alpha < 0.005 || radius <= 0.01) return;
    if (!intersectsBound(ray, sceneDistance, origin, radius * 1.2 + 0.4)) return;

    chronoFragments(ray, sceneDistance, wave, light);
    chronoShell(ray, sceneDistance, origin, radius, alpha, age, 1.0, collapse, isSky,
                light, darkness, chipAlpha, chipColor, distortion, skyOverride, skyOverrideAlpha);
    chronoCorona(ray, sceneDistance, origin, radius, alpha, light);

    // Subtle inner harmonic resonance shell (like Gravitational Dissonance echo)
    float innerScale = 0.86 + 0.02 * sin(age * 5.0);
    float dummyDark = 0.0;
    vec3 dummySky = vec3(0.0);
    float dummySkyA = 0.0;
    chronoShell(ray, sceneDistance, origin, radius * innerScale, alpha * 0.24, age * 1.15, 0.0, collapse, false,
                light, dummyDark, chipAlpha, chipColor, distortion, dummySky, dummySkyA);

    // Ground ring & world-geometry intersection contact trace
    goldenFloorRing(ray, sceneDistance, origin - vec3(0.0, 0.86, 0.0), radius, 0.095, alpha * 0.65, CHRONO_NEON, light);
    goldenFloorRing(ray, sceneDistance, origin - vec3(0.0, 0.86, 0.0), radius * 0.72, 0.045, alpha * 0.28, CHRONO_PURPLE, light);
    if (!isSky) {
        vec3 p = scenePoint - origin;
        float band = exp(-pow((length(p) - radius) / 0.09, 2.0));
        light += CHRONO_NEON * band * alpha * 0.42;
    }

    chronoSingularity(ray, sceneDistance, origin, collapse, alpha, light, distortion);
}

void computeChronoStunWarp(float chronoStunStrength, float chronoStunAge, vec2 maskUv,
                           inout vec2 pressure, inout vec2 split) {
    if (chronoStunStrength <= 0.001) return;
    float s = clamp(chronoStunStrength, 0.0, 1.0);
    float r = length(maskUv);
    vec2 radial = r > 0.0001 ? maskUv / r : vec2(0.0);
    vec2 tangent = vec2(-radial.y, radial.x);
    float edge = smoothstep(0.16, 0.95, r);
    float w1 = softNoise(maskUv * 3.4 + vec2(chronoStunAge * 0.25, -chronoStunAge * 0.18)) - 0.5;
    float w2 = softNoise(maskUv * 7.2 + vec2(-chronoStunAge * 0.42, chronoStunAge * 0.31)) - 0.5;
    float beat = pow(0.5 + 0.5 * sin(chronoStunAge * 5.2), 2.0);
    pressure += (radial * (0.008 + 0.007 * beat) + tangent * 0.009 + vec2(w1, w2) * 0.014) * edge * s;
    split += (radial * 0.0042 + tangent * 0.0020) * edge * s;
}

void applyChronoStunOverlay(float chronoStunStrength, float chronoStunAge, vec2 maskUv, inout vec3 color) {
    if (chronoStunStrength <= 0.001) return;
    float s = clamp(chronoStunStrength, 0.0, 1.0);
    float r = length(maskUv);
    float ang = atan(maskUv.y, maskUv.x);

    // Swirling arcane purple tendrils & deep noise mist crawling inward from screen edges
    float nBroad = softNoise(vec2(ang * 2.4 + chronoStunAge * 0.35, r * 3.8 - chronoStunAge * 0.55));
    float nFine = softNoise(maskUv * 8.5 + vec2(-chronoStunAge * 0.45, chronoStunAge * 0.38));
    float tendrils = clamp((nBroad * 0.65 + nFine * 0.35), 0.0, 1.0);
    float radialVeins = pow(max(0.0, sin(ang * 7.0 + nBroad * 5.2 - chronoStunAge * 1.15)), 4.0);

    float outerVignette = smoothstep(0.20, 0.96, r + (tendrils - 0.5) * 0.24);
    float deepEdge = smoothstep(0.42, 1.12, r + (tendrils - 0.5) * 0.16);
    float pulse = 0.80 + 0.20 * sin(chronoStunAge * 4.8);
    float surge = exp(-chronoStunAge * 5.5) * 0.65;

    // 12-hour arcane time-lock ring & ticks framing the stunned victim's vision
    float clockRing = exp(-pow((r - 0.52 - 0.012 * sin(chronoStunAge * 3.2)) / 0.014, 2.0));
    float clockTicks = pow(max(0.0, cos(ang * 12.0 + chronoStunAge * 0.22)), 20.0)
                     * exp(-pow((r - 0.52) / 0.042, 2.0));
    float innerHalo = exp(-pow((r - 0.38) / 0.028, 2.0)) * 0.32;

    // Wash world in stopped cosmic purple + abyssal edge darkening + bright neon arcane tendrils
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    vec3 mutedWorld = mix(color, vec3(luma), 0.52 * s);
    vec3 purpleTintWorld = mutedWorld * vec3(0.62, 0.32, 1.02) + vec3(0.05, 0.005, 0.11);
    color = mix(color, purpleTintWorld, (0.42 + 0.40 * outerVignette) * s);

    // Abyssal dark-violet corners so the borders feel closed off by stopped time
    color = mix(color, vec3(0.06, 0.008, 0.15), clamp(deepEdge * 0.72 * s, 0.0, 0.82));

    // Luminous purple-magenta magic arcs, tendrils, and time-lock clock ring
    vec3 magicGlow = CHRONO_PURPLE * (outerVignette * 0.55 + radialVeins * outerVignette * 0.75)
                   + CHRONO_NEON * ((clockRing * 0.65 + clockTicks * 0.95 + innerHalo) * pulse + tendrils * deepEdge * 0.48)
                   + CHRONO_CORE * (clockTicks * 0.45 + surge * (0.30 + 0.70 * outerVignette));
    color += magicGlow * s;
}
