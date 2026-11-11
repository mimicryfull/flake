// Class: Concussed ("Контужений") — Skill 1: Gravitational Dissonance ("Гравітаційний дисонанс")

// Analytic sphere intersection: no hundreds-of-steps raymarch and no particle mesh.
// Accumulate only world-space surfaces in front of the opaque scene.
void shell(vec3 ray, float sceneDistance, vec3 origin, float radius, float alpha,
           float age, float detail, float charging, inout vec3 light, inout float tint, inout vec2 distortion) {
    // Subtle breathing ellipsoid, exact intersection and inverse-transpose normal.
    // Animation radius/spring is unchanged; deformation only adds restrained surface inertia.
    vec3 axes = vec3(1.0 + charging * 0.035 * sin(age * 8.0),
                     1.0 + charging * 0.055 * sin(age * 8.0 - 0.9), 1.0);
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
    float fresnel = pow(1.0 - facing, 3.0) * visible;
    float border = exp(-facing * facing * 13.0) * visible;
    float outline = exp(-pow((facing - 0.48) / 0.10, 2.0)) * visible;

    // Low-contrast moving caustics keep the membrane alive without filling it with sparks.
    float flow = sin(normal.x * 19.0 + normal.z * 11.0 - age * 7.0)
               * sin(normal.y * 23.0 - normal.x * 8.0 + age * 4.0);
    float membrane = (0.27 + charging * 0.23 + 0.035 * flow) * visible;
    float spiral = pow(max(0.0, sin(atan(normal.z, normal.x) * 3.0 + normal.y * 12.0 - age * age * 45.0)), 12.0)
                 * charging * visible * 0.42;
    float floorRing = exp(-pow((point.y + 0.94) / 0.055, 2.0)) * visible * detail;
    float latitude = exp(-pow(normal.y / 0.025, 2.0)) * 0.075 * visible * detail;
    float density = 1.0 - exp(-(exit - entry) * 0.22);
    float diffuse = 0.5 + 0.5 * dot(normal, normalize(vec3(-0.4, 0.8, 0.3)));
    float nacre = 0.5 + 0.5 * sin(facing * 11.0 + flow * 0.8 - age * 2.0);
    vec3 pearl = mix(CYAN, ICE, nacre * 0.65 + diffuse * 0.25);
    // Looking out from inside does not paint the whole screen cyan.
    float interiorScale = inside ? 0.28 : 1.0;
    tint += alpha * (membrane * interiorScale + fresnel * 0.22 + density * (0.025 + charging * 0.09) * detail);
    float viewScale = inside && charging > 0.5 ? 0.3 : 1.0;
    light += alpha * viewScale * (pearl * (fresnel * 0.22 + charging * diffuse * density * 0.09) + ICE * border * 1.1
                   + CYAN * (floorRing * 0.65 + latitude + spiral) - vec3(outline * 0.10));

    // Project the WORLD normal, not normal.xy (which rotates incorrectly with the camera).
    vec2 direction = screenPosition(origin + normal * radius) - screenPosition(origin);
    float directionLength = length(direction);
    direction = directionLength > 0.0001 ? direction / directionLength : vec2(0.0);
    distortion += direction * (fresnel * 0.75 + border * 0.25) * alpha;
}

void corona(vec3 ray, float sceneDistance, vec3 origin, float radius, float alpha, inout vec3 light) {
    float along = dot(ray, origin);
    if (along <= 0.0 || length(origin) <= radius) return;
    float impact = length(origin - ray * along);
    float width = 0.06 + radius * 0.055;
    float halo = exp(-pow((impact - radius) / width, 2.0));
    float front = along - sqrt(max(0.0, radius * radius - impact * impact));
    halo *= smoothstep(-0.02, 0.04, sceneDistance - front);
    light += ICE * halo * alpha * 0.32;
}

void fragments(vec3 ray, float sceneDistance, int wave, inout vec3 light) {
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
        light += mix(CYAN, ICE, along) * streak * alpha * 0.7;
    }
}

void singularity(vec3 ray, float sceneDistance, vec3 origin, float progress, float alpha,
                 inout vec3 light, inout vec2 distortion) {
    float collapse = smoothstep(0.5, 0.95, progress);
    float along = dot(ray, origin);
    if (along <= 0.0 || length(origin) < 1.8 || collapse <= 0.0) return;
    float impact = length(ray * along - origin);
    float lens = exp(-impact * impact * 5.0) * collapse * alpha;
    if (along > sceneDistance + 0.02) return;
    vec2 radial = texCoord - screenPosition(origin);
    distortion -= radial / max(0.001, length(radial)) * lens * 2.2;
    float flare = exp(-pow(impact / 0.11, 2.0)) * collapse;
    light += ICE * flare * alpha * 0.85;
}

void pressureRing(vec3 ray, float sceneDistance, vec3 origin, float radius, float alpha, inout vec3 light) {
    if (abs(ray.y) < 0.00001) return;
    float hit = (origin.y - 0.94) / ray.y;
    if (hit <= 0.0 || hit > sceneDistance + 0.015) return;
    float distanceToCenter = length((ray * hit - origin).xz);
    float line = exp(-pow((distanceToCenter - radius) / 0.065, 2.0));
    float wake = exp(-pow((distanceToCenter - radius * 0.87) / 0.11, 2.0)) * 0.16;
    light += ICE * (line + wake) * alpha * 0.58;
}

void gravitationalDissonanceWave(vec3 ray, float sceneDistance, vec3 scenePoint, float depth, int wave,
                                 inout vec3 light, inout float tint, inout vec2 distortion) {
    float alpha = WaveStyles[wave].x;
    vec3 origin = WaveOrigins[wave].xyz;
    float radius = WaveOrigins[wave].w;
    float age = WaveStyles[wave].y;
    float charging = WaveStyles[wave].z;

    if (!intersectsBound(ray, sceneDistance, origin, charging > 0.5 ? radius * 1.15 + 0.2 : 6.5)) return;
    if (charging < 0.5) fragments(ray, sceneDistance, wave, light);
    // Ejecta outlive the membrane, without keeping an almost-transparent sphere alive.
    if (alpha < 0.005) return;
    shell(ray, sceneDistance, origin, radius, alpha, age, 1.0, charging, light, tint, distortion);
    corona(ray, sceneDistance, origin, radius, alpha, light);
    if (charging > 0.5) {
        // Dense luminous core and a translucent outer shell during accumulation.
        float core = radius * 0.26;
        shell(ray, sceneDistance, origin, core, alpha * 0.75, age, 0.0, 1.0, light, tint, distortion);
        singularity(ray, sceneDistance, origin, WaveStyles[wave].w, alpha, light, distortion);
        return;
    }
    pressureRing(ray, sceneDistance, origin, radius, alpha, light);
    // One faint pressure echo, not a stack of competing bright spheres.
    float echo = smoothstep(0.08, 0.25, age) * 0.18;
    shell(ray, sceneDistance, origin, radius * 0.84, alpha * echo, age, 0.0, 0.0, light, tint, distortion);

    // A fine contact trace where the wave crosses the floor / a wall / a mob.
    if (depth > 0.000001) {
        vec3 p = scenePoint - origin;
        float band = exp(-pow((length(p) - radius) / 0.075, 2.0));
        light += CYAN * band * alpha * 0.20;
    }
}
