#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

layout(std140) uniform ShockwaveConfig {
    mat4 ViewProjection;
    mat4 InverseViewProjection;
    vec4 DepthConfig;
    vec4 WaveOrigins[4]; // Camera-relative xyz, radius in blocks.
    vec4 WaveStyles[4];  // Opacity, age in seconds, charging flag, charge progress.
    vec4 ImpactConfig; // Signed pressure, aberration envelope, screen-space centre xy.
    vec4 MoteOrigins[128]; // 32 per wave; negative radius denotes a ground fragment.
    vec4 MoteTails[128];
    vec4 QuakeMaterials[128]; // Sampled block colour, rotation angle.
    vec4 QuakeStarts[256]; // Cached terrain channel start, width.
    vec4 QuakeEnds[256]; // Progressively revealed endpoint, intensity.
    vec4 QuakeBounds[32]; // Eight small conservative spatial groups per cast.
};

const vec3 CYAN = vec3(0.82, 0.91, 1.0);
const vec3 ICE = vec3(1.0, 0.985, 0.96);
const vec3 ELECTRIC_BLUE = vec3(0.18, 0.56, 1.0);
const vec3 ELECTRIC_CYAN = vec3(0.42, 0.88, 1.0);
const vec3 ELECTRIC_CORE = vec3(0.88, 0.97, 1.0);
const vec3 VOID_VIOLET = vec3(0.42, 0.10, 0.80);
const vec3 VOID_BRIGHT = vec3(0.88, 0.58, 1.0);
const vec3 CRYSTAL_DEEP = vec3(0.16, 0.52, 0.86);
const vec3 CRYSTAL_BRIGHT = vec3(0.72, 0.95, 1.0);
const vec3 CRYSTAL_TINT = vec3(0.46, 0.82, 1.0);
const vec3 CRIMSON = vec3(0.44, 0.015, 0.055);
const vec3 CRIMSON_HOT = vec3(0.86, 0.14, 0.16);
const vec3 CRIMSON_WHITE = vec3(1.0, 0.58, 0.55);
const vec3 BLOOD_DARK = vec3(0.16, 0.002, 0.02);

// Conservative ray/sphere rejection before any expensive surface/particle work.
bool intersectsBound(vec3 ray, float sceneDistance, vec3 centre, float radius) {
    float along = dot(ray, centre);
    if (along + radius < 0.0 || along - radius > sceneDistance) return false;
    return dot(centre, centre) - along * along <= radius * radius;
}

vec3 unproject(float depth) {
    vec4 p = InverseViewProjection * vec4(texCoord * 2.0 - 1.0, depth * DepthConfig.x + DepthConfig.y, 1.0);
    return p.xyz / p.w;
}

vec2 screenPosition(vec3 point) {
    vec4 clip = ViewProjection * vec4(point, 1.0);
    return clip.xy / max(abs(clip.w), 0.0001) * 0.5 + 0.5;
}

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

// ---- Cube of Darkness -------------------------------------------------------
// Dense dark orb with an unstable bright violet rim. Depth-tested analytic sphere, no mesh.
void voidOrb(vec3 ray, float sceneDistance, vec3 centre, float radius, float alpha, float age,
             inout vec3 light, inout float darkness) {
    if (radius <= 0.001 || alpha <= 0.0) return;
    float along = dot(ray, centre);
    if (along <= 0.0) return;
    float impact = length(ray * along - centre);
    if (impact > radius * 3.2) return;
    float front = along - sqrt(max(0.0, radius * radius - impact * impact));
    float visible = smoothstep(-0.02, 0.05, sceneDistance - front);
    float angle = atan(ray.x * centre.z - ray.z * centre.x, impact + 0.001);
    float unstable = 0.78 + 0.22 * sin(angle * 9.0 + age * 55.0) * sin(impact * 40.0 - age * 31.0);
    float body = smoothstep(radius, radius * 0.5, impact);
    float rim = exp(-pow((impact - radius) / (radius * 0.16 + 0.008), 2.0)) * unstable;
    float glow = exp(-impact * impact / (radius * radius * 2.6));
    darkness += body * alpha * visible * 0.9;
    light += (VOID_BRIGHT * rim * 0.95 + VOID_VIOLET * glow * 0.32) * alpha * visible;
}

// Capsule streak: black turbulent core, bright jittering edge, violet halo.
void voidSegment(vec3 ray, float sceneDistance, vec3 start, vec3 end, float width, float alpha, float coreDark,
                 inout vec3 light, inout float darkness) {
    vec3 segment = end - start;
    vec3 centre = (start + end) * 0.5;
    if (!intersectsBound(ray, sceneDistance, centre, length(segment) * 0.5 + width * 4.0)) return;
    float b = dot(ray, segment), d = dot(ray, start), e = dot(segment, start);
    float along = clamp((b * d - e) / max(0.000001, dot(segment, segment) - b * b), 0.0, 1.0);
    vec3 point = start + segment * along;
    float hit = dot(point, ray);
    if (hit <= 0.0 || hit > sceneDistance + 0.008) return;
    float dist = length(ray * hit - point);
    if (dist > width * 4.0) return;
    float core = 1.0 - smoothstep(width * 0.45, width * 0.75, dist);
    float edge = exp(-pow((dist - width * 0.85) / (width * 0.38), 2.0));
    float halo = exp(-dist * dist / (width * width * 7.0));
    darkness += core * alpha * coreDark;
    light += (VOID_BRIGHT * edge * 1.1 + VOID_VIOLET * halo * 0.40) * alpha;
}

vec3 boltPoint(vec3 start, vec3 target, vec3 side, vec3 up, float t, float age, float span) {
    // Homing arc that straightens as the shot accelerates, plus damped procedural turbulence.
    float bend = sin(t * 3.14159) * span * 0.07 * (1.0 - smoothstep(0.0, 0.5, age - 0.14));
    float envelope = sin(t * 3.14159);
    float wob = 0.075 * envelope * exp(-max(0.0, age - 0.30) * 6.0);
    vec3 turbulence = side * sin(t * 31.0 - age * 70.0) * wob + up * sin(t * 23.0 + age * 58.0 + 1.7) * wob;
    return mix(start, target, t) + side * bend + turbulence;
}

void darkBolt(vec3 ray, float sceneDistance, int wave, inout vec3 light, inout float darkness, inout vec2 distortion) {
    vec3 start = WaveOrigins[wave].xyz;
    float span = WaveOrigins[wave].w;
    vec3 target = QuakeBounds[wave * 8].xyz;
    float age = WaveStyles[wave].y;
    float alpha = WaveStyles[wave].x;
    vec4 anim = QuakeMaterials[wave * 32]; // orb radius, head, tail, impact progress
    vec3 centre = (start + target) * 0.5;
    if (!intersectsBound(ray, sceneDistance, centre, span * 0.5 + 1.8)) return;

    voidOrb(ray, sceneDistance, start, anim.x, alpha, age, light, darkness);

    vec3 dir = (target - start) / max(span, 0.001);
    vec3 side = normalize(cross(dir, vec3(0.0, 1.0, 0.0)) + vec3(0.0001));
    vec3 up = cross(side, dir);
    if (anim.y > 0.0 && anim.z < 0.999) {
        float t0 = anim.z;
        float t1 = anim.y;
        vec3 previous = boltPoint(start, target, side, up, t0, age, span);
        for (int i = 1; i <= 8; i++) {
            float k = float(i) / 8.0;
            float t = mix(t0, t1, k);
            vec3 next = boltPoint(start, target, side, up, t, age, span);
            // Thin tail, thick bright head.
            float width = 0.018 + 0.060 * k * k;
            voidSegment(ray, sceneDistance, previous, next, width, alpha * (0.35 + 0.65 * k), 1.0, light, darkness);
            previous = next;
        }
        voidOrb(ray, sceneDistance, previous, 0.11, alpha, age, light, darkness);
    }

    // Impact: contracting dark core, one expanding violet ring, brief spatial lens and flying shards.
    float hit = anim.w;
    if (hit > 0.0) {
        float env = (1.0 - hit) * (1.0 - hit);
        float travel = 1.0 - pow(1.0 - hit, 3.0);
        voidOrb(ray, sceneDistance, target, 0.30 * (1.0 - hit * hit) + 0.04, env, age, light, darkness);
        float along = dot(ray, target);
        if (along > 0.0) {
            float impact = length(ray * along - target);
            float ring = exp(-pow((impact - (0.18 + 1.05 * travel)) / (0.05 + 0.08 * hit), 2.0));
            if (along - impact * 0.0 < sceneDistance + 0.3) light += VOID_BRIGHT * ring * env * 0.7;
            vec2 radial = texCoord - screenPosition(target);
            float lens = exp(-impact * impact / (0.35 + 0.9 * travel)) * env;
            distortion += radial / max(0.001, length(radial)) * lens * 0.9;
        }
        for (int i = 0; i < 8; i++) {
            int index = wave * 32 + i;
            float a = MoteTails[index].w;
            if (a <= 0.0) continue;
            voidSegment(ray, sceneDistance, MoteOrigins[index].xyz, MoteOrigins[index].xyz + MoteTails[index].xyz,
                        MoteOrigins[index].w, a, 0.0, light, darkness);
        }
    }
}

// ---- Matadora ---------------------------------------------------------------
float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float softNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x), f.y);
}

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

// Voxel-style blood drop: axis-aligned cube with a darker core and a lit rim.
void bloodCube(vec3 ray, float sceneDistance, vec3 centre, vec3 ext, float alpha,
               inout vec3 light, inout float chipAlpha, inout vec3 chipColor) {
    vec3 safeRay = mix(vec3(-1.0), vec3(1.0), step(vec3(0.0), ray)) * max(abs(ray), vec3(0.00001));
    vec3 a = (-ext - centre) / safeRay, b = (ext - centre) / safeRay;
    vec3 nearT = min(a, b), farT = max(a, b);
    float hit = max(max(nearT.x, nearT.y), nearT.z), exit = min(min(farT.x, farT.y), farT.z);
    if (hit <= 0.0 || exit < hit || hit > sceneDistance) return;
    vec3 p = ray * hit - centre;
    vec3 q = abs(p) / ext;
    float edge = max(min(q.x, q.y), min(max(q.x, q.y), q.z));
    float rim = smoothstep(0.55, 1.0, edge);
    vec3 col = mix(BLOOD_DARK, CRIMSON, 0.25 + 0.75 * rim);
    col = mix(col, CRIMSON_HOT, pow(rim, 3.0) * 0.35);
    chipColor += col * alpha;
    chipAlpha += alpha * 0.95;
    light += CRIMSON_HOT * rim * alpha * 0.22;
}

// Thin oriented slab used as the cut: bright scarlet spine along the blade line, tapered ends.
void critCut(vec3 ray, float sceneDistance, vec3 origin, mat3 basis, vec3 ext, float alpha,
             inout vec3 light, inout float chipAlpha, inout vec3 chipColor) {
    vec3 ro = transpose(basis) * -origin;
    vec3 rd = transpose(basis) * ray;
    vec3 safeRd = mix(vec3(-1.0), vec3(1.0), step(vec3(0.0), rd)) * max(abs(rd), vec3(0.00001));
    vec3 a = (-ext - ro) / safeRd, b = (ext - ro) / safeRd;
    vec3 nearT = min(a, b), farT = max(a, b);
    float hit = max(max(nearT.x, nearT.y), nearT.z), exit = min(min(farT.x, farT.y), farT.z);
    if (hit <= 0.0 || exit < hit || hit > sceneDistance) return;
    vec3 p = ro + rd * hit;
    vec3 q = abs(p) / ext;
    float spine = clamp(1.0 - q.y, 0.0, 1.0);
    float taper = 1.0 - smoothstep(0.55, 1.0, q.x);
    float body = spine * taper * alpha;
    chipColor += mix(CRIMSON, CRIMSON_WHITE, pow(spine, 6.0)) * body;
    chipAlpha += body * 0.9;
    light += CRIMSON_HOT * body * 0.35;
}

// Blood cut: two thin slashes across the target plus a faint glow; square drops fall to the ground.
void critBurst(vec3 ray, float sceneDistance, int wave,
               inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    vec4 body = QuakeBounds[wave * 8];
    if (body.w <= 0.0) return;
    vec3 origin = body.xyz;
    float scale = clamp(body.w / 2.4, 0.4, 4.0);
    float alpha = WaveStyles[wave].x;
    float age = WaveStyles[wave].y;
    float seed = WaveStyles[wave].w * 6.28318;
    vec3 aim = QuakeMaterials[wave * 32].xyz;
    float aimLen = length(aim);
    vec3 A = aimLen > 0.0001 ? aim / aimLen : vec3(0.0, 0.0, -1.0);
    float groundY = QuakeMaterials[wave * 32 + 1].x;
    if (!intersectsBound(ray, sceneDistance, origin, scale * 2.6)) return;

    // X runs along the diagonal slash, Y is the thin width axis, A faces the attacker.
    vec3 up = vec3(0.0, 1.0, 0.0);
    vec3 side = cross(A, up);
    side = length(side) > 0.001 ? normalize(side) : vec3(1.0, 0.0, 0.0);
    vec3 X = normalize(side * 0.8 - up * 0.6);
    vec3 Y = normalize(cross(A, X));

    float draw = smoothstep(0.0, 0.09, age);
    float cutAlpha = alpha * (1.0 - smoothstep(0.16, 0.42, age));
    float reach = scale * 1.35 * draw;
    if (cutAlpha > 0.001) {
        critCut(ray, sceneDistance, origin, mat3(X, Y, A), vec3(reach, 0.035 * scale, 0.12 * scale),
                cutAlpha, light, chipAlpha, chipColor);
        vec3 X2 = normalize(X + Y * 0.3);
        vec3 Y2 = normalize(cross(A, X2));
        critCut(ray, sceneDistance, origin + Y * (0.07 * scale), mat3(X2, Y2, A),
                vec3(reach * 0.62, 0.02 * scale, 0.12 * scale), cutAlpha, light, chipAlpha, chipColor);
        critCut(ray, sceneDistance, origin, mat3(X, Y, A), vec3(reach * 1.05, 0.09 * scale, 0.2 * scale),
                cutAlpha * 0.22, light, chipAlpha, chipColor);
    }

    // Blood chunks leave the cut line, fly forward, then fall under gravity and settle as flat squares.
    int drops = DepthConfig.z < 0.5 ? 6 : (DepthConfig.z < 1.5 ? 9 : 12);
    const float gravity = 9.8;
    for (int k = 0; k < 12; k++) {
        if (k >= drops) break;
        float fk = float(k);
        float h1 = fract(fk * 0.618034 + seed * 0.71);
        float h2 = fract(fk * 0.414214 + seed * 0.37);
        float h3 = fract(fk * 0.732051 + seed * 0.23);
        float local = age - (0.012 + 0.05 * h1);
        if (local <= 0.0) continue;
        vec3 start = origin + X * ((h2 * 2.0 - 1.0) * reach * 0.85);
        vec3 vel = normalize(A * (0.6 + 0.4 * h3) + up * (0.25 + 0.6 * h1) + (vec3(h1, h2, h3) - 0.5) * 0.7)
                 * (1.2 + 1.6 * h2) * scale;
        float tLand = (vel.y + sqrt(vel.y * vel.y + 2.0 * gravity * max(start.y - groundY, 0.0))) / gravity;
        float size = scale * (0.016 + 0.012 * h1);
        float fade = alpha;
        if (local < tLand) {
            vec3 pos = start + vel * local;
            pos.y -= 0.5 * gravity * local * local;
            bloodCube(ray, sceneDistance, pos, vec3(size, size * 1.25, size), fade, light, chipAlpha, chipColor);
        } else {
            float since = local - tLand;
            float puddle = (1.0 - smoothstep(0.12, 0.5, since)) * fade;
            vec3 pos = vec3(start.x + vel.x * tLand, groundY + 0.006 * scale, start.z + vel.z * tLand);
            bloodCube(ray, sceneDistance, pos, vec3(size * 1.5, 0.006 * scale, size * 1.5), puddle, light, chipAlpha, chipColor);
        }
    }
}

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

// ---- Bastion ----------------------------------------------------------------
const vec3 BASTION_GOLD = vec3(1.0, 0.64, 0.065);
const vec3 BASTION_PALE = vec3(1.0, 0.80, 0.32);
const vec3 BASTION_WHITE = vec3(1.0, 0.98, 0.86);
const vec3 BASTION_AMBER = vec3(0.34, 0.17, 0.018);
const vec3 DUST = vec3(0.62, 0.47, 0.25);

// Same damped step as BastionAnimation. Acceleration, overshoot, then a short settling tail.
float bastionSpring(float t) {
    if (t <= 0.0) return 0.0;
    if (t >= 1.0) return 1.0;
    return 1.0 - exp(-8.0 * t) * (cos(11.0 * t) + 0.5 * sin(11.0 * t));
}

mat3 bastionBasis(vec3 normal, float roll) {
    vec3 x = normalize(cross(vec3(0.0, 1.0, 0.001), normal));
    vec3 y = cross(normal, x);
    return mat3(x * cos(roll) + y * sin(roll), y * cos(roll) - x * sin(roll), normal);
}

// A convex, thick, tapered shield with clipped corners and a ridged front face.
// Exact half-space intersection: actual facets and depth from EVERY camera angle, no raymarch.
const vec3 BASTION_PLANES[11] = vec3[11](
    vec3(1,0,0), vec3(-1,0,0), vec3(0,1,0), vec3(0,-1,0),
    vec3(0.78,0.63,0), vec3(-0.78,0.63,0), vec3(0.78,-0.63,0), vec3(-0.78,-0.63,0),
    vec3(0,0,-1), vec3(0.28,0.08,1), vec3(-0.28,0.08,1));
const float BASTION_LIMITS[11] = float[11](1,1,1,1,1.05,1.05,0.88,0.88,1,1,1);

void bastionPlate(vec3 ray, float sceneDistance, vec3 centre, mat3 basis, vec3 ext,
                  float alpha, float age, float seed, float excitation, float dissolve,
                  inout vec3 light, inout float darkness) {
    float maxExt = max(ext.x, max(ext.y, ext.z)) * 1.42;
    if (alpha < 0.001 || !intersectsBound(ray, sceneDistance, centre, maxExt)) return;
    vec3 ro = (transpose(basis) * -centre) / ext;
    vec3 rd = (transpose(basis) * ray) / ext;
    // Fast vectorized slab test for the 5 axis-aligned box faces (x = +-1, y = +-1, z = -1) before bevel planes.
    vec3 safeRd = mix(vec3(-1.0), vec3(1.0), step(vec3(0.0), rd)) * max(abs(rd), vec3(0.000001));
    vec3 t0 = (-vec3(1.0) - ro) / safeRd;
    vec3 t1 = (vec3(1.0, 1.0, 1.25) - ro) / safeRd;
    vec3 nearBox = min(t0, t1), farBox = max(t0, t1);
    float nearHit = max(max(nearBox.x, nearBox.y), nearBox.z);
    float farHit = min(min(farBox.x, farBox.y), farBox.z);
    if (farHit < max(nearHit, 0.0) || nearHit > sceneDistance) return;

    int nearFace = nearBox.x > nearBox.y && nearBox.x > nearBox.z ? (rd.x < 0.0 ? 0 : 1)
                 : (nearBox.y > nearBox.z ? (rd.y < 0.0 ? 2 : 3) : 8);
    int farFace = farBox.x < farBox.y && farBox.x < farBox.z ? (rd.x > 0.0 ? 0 : 1)
                : (farBox.y < farBox.z ? (rd.y > 0.0 ? 2 : 3) : 8);

    // Only test the 6 bevel & ridged front planes (indices 4..7, 9..10).
    for (int i = 4; i < 11; i++) {
        if (i == 8) continue;
        float d = dot(BASTION_PLANES[i], rd);
        float h = BASTION_LIMITS[i] - dot(BASTION_PLANES[i], ro);
        if (abs(d) < 0.000001) { if (h < 0.0) return; }
        else {
            float t = h / d;
            if (d < 0.0 && t > nearHit) { nearHit = t; nearFace = i; }
            if (d > 0.0 && t < farHit) { farHit = t; farFace = i; }
        }
    }
    if (farHit < max(nearHit, 0.0)) return;
    float hit = nearHit > 0.0 ? nearHit : farHit;
    if (hit <= 0.0 || hit > sceneDistance) return;
    int face = nearHit > 0.0 ? nearFace : farFace;
    vec3 p = ro + rd * hit;
    vec3 normal = normalize(basis * (BASTION_PLANES[face] / ext));
    float facing = abs(dot(normal, ray));
    float invFacing = 1.0 - facing;
    float fresnel = invFacing * invFacing * sqrt(max(0.0, invFacing));
    // Silhouette inset follows the polygon, not a square UV border.
    float border = min(min(1.0 - abs(p.x), 1.0 - abs(p.y)),
                   min(min(1.05 - dot(BASTION_PLANES[4].xy, abs(p.xy)), 0.88 - dot(vec2(0.78, 0.63), vec2(abs(p.x), -p.y))), 10.0));
    float aa = max(0.008, hit * 0.0009 / min(ext.x, ext.y));
    float rim = 1.0 - smoothstep(0.015, 0.038 + aa, border);
    float insetDiff = (border - 0.13) / (0.014 + aa);
    float inset = exp(-insetDiff * insetDiff);
    float spineDiff = p.x / 0.035;
    float spine = exp(-spineDiff * spineDiff) * (1.0 - smoothstep(0.55, 0.95, abs(p.y)));
    float nacre = 0.5 + 0.5 * sin(p.y * 9.0 + sin(p.x * 7.0 + seed) * 1.4 - age * 1.5 + seed);
    float lamBase = 0.5 + 0.5 * sin(p.y * 31.0 + p.x * 12.0 - age * 2.2 + seed);
    float lam2 = lamBase * lamBase; float lam4 = lam2 * lam2; float lamina = lam4 * lam4;
    float scanDiff = (p.y - sin(age * 1.8 + seed) * 1.2) / 0.16;
    float scan = exp(-scanDiff * scanDiff);
    float intact = 1.0;
    float burn = 0.0;
    if (dissolve > 0.001) {
        float erosion = softNoise(p.xy * 5.0 + vec2(seed, age * 0.9));
        intact = 1.0 - smoothstep(erosion - 0.13, erosion + 0.13, dissolve * 1.2 - 0.12);
        float burnDiff = (erosion - dissolve * 1.2 + 0.12) / 0.08;
        burn = exp(-burnDiff * burnDiff);
    }
    float visible = smoothstep(0.0, 0.045, sceneDistance - hit) * intact * alpha;
    vec3 pearl = mix(BASTION_GOLD, BASTION_PALE, nacre * 0.72 + fresnel * 0.28);
    float body = 0.068 + nacre * 0.042 + lamina * 0.024;
    light += (pearl * (body + fresnel * 0.13 + inset * 0.11 + scan * 0.055)
           + mix(BASTION_GOLD, BASTION_PALE, 0.35) * rim * 0.42
           + BASTION_WHITE * (spine * 0.08 + excitation * (0.30 + rim * 0.68) + burn * 0.65)) * visible;
    // Thin amber occlusion gives thickness without hiding the player behind opaque armour.
    float faceShade = max(0.0, dot(normal, normalize(vec3(-0.4, 0.8, 0.25))));
    darkness += (0.052 + (1.0 - faceShade) * 0.040 + float(face == 8) * 0.038) * visible * (1.0 - rim * 0.85);
}

void ramStreak(vec3 ray, float sceneDistance, vec3 start, vec3 end, float width, float alpha,
               vec3 colour, inout vec3 light) {
    vec3 segment = end - start;
    vec3 centre = (start + end) * 0.5;
    if (!intersectsBound(ray, sceneDistance, centre, length(segment) * 0.5 + width * 3.5)) return;
    float b = dot(ray, segment), d = dot(ray, start), e = dot(segment, start);
    float along = clamp((b * d - e) / max(0.000001, dot(segment, segment) - b * b), 0.0, 1.0);
    vec3 point = start + segment * along;
    float hit = dot(point, ray);
    if (hit <= 0.0 || hit > sceneDistance + 0.01) return;
    float dist = length(ray * hit - point);
    if (dist > width * 3.5) return;
    float core = 1.0 - smoothstep(width * 0.32, width * 0.82, dist);
    float halo = exp(-dist * dist / (width * width * 4.5));
    light += colour * (core * 0.62 + halo * 0.26) * alpha;
}

void dustPuff(vec3 ray, float sceneDistance, vec3 centre, float radius, float alpha,
              inout vec3 light, inout float darkness) {
    float along = dot(ray, centre);
    if (along <= 0.0) return;
    float impact = length(ray * along - centre);
    if (impact > radius * 2.6) return;
    float front = along - sqrt(max(0.0, radius * radius - impact * impact));
    float visible = smoothstep(-0.02, 0.06, sceneDistance - front);
    float glow = exp(-impact * impact / (radius * radius * 1.35));
    light += DUST * glow * alpha * 0.46 * visible;
    darkness += glow * alpha * 0.15 * visible;
}

void goldenFloorRing(vec3 ray, float sceneDistance, vec3 centre, float radius, float width, float alpha, vec3 col, inout vec3 light) {
    if (alpha <= 0.001 || abs(ray.y) < 0.0001) return;
    float hit = centre.y / ray.y;
    if (hit <= 0.0 || hit > sceneDistance + 0.02) return;
    vec2 rel = (ray * hit - centre).xz;
    float r = length(rel);
    float diff = (r - radius) / width;
    if (abs(diff) > 3.2) return;
    float band = exp(-diff * diff);
    light += col * band * alpha;
}

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

// ---- Ghoul: living, viscous blood ribbons + crimson mist & droplets ----------
const vec3 GHOUL_DEEP = vec3(0.22, 0.012, 0.048);
const vec3 GHOUL_BLOOD = vec3(0.58, 0.032, 0.125);
const vec3 GHOUL_RED = vec3(0.88, 0.072, 0.195);
const vec3 GHOUL_RUBY = vec3(1.0, 0.185, 0.31);
const vec3 GHOUL_HOT = vec3(1.0, 0.74, 0.78);

// Anisotropic finite capsule: a twisted, thick membrane, NOT a billboard or laser.
// Fast isotropic ray-to-segment rejection runs BEFORE computing basis/trig.
void ghoulRibbon(vec3 ray, float sceneDistance, vec3 start, vec3 end, float width, float alpha,
                 float age, float seed, int kind, vec3 body, float feeding,
                 inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor) {
    vec3 segment = end - start;
    float span2 = dot(segment, segment);
    if (span2 < 0.00001 || width <= 0.0 || alpha <= 0.001) return;
    // Fast isotropic closest-approach check before any trig or matrix math:
    float b0 = dot(ray, segment), d0 = dot(ray, start), e0 = dot(segment, start);
    float along0 = clamp((b0 * d0 - e0) / max(0.00001, span2 - b0 * b0), 0.0, 1.0);
    vec3 midPt = start + segment * along0;
    float hit0 = dot(midPt, ray);
    if (hit0 <= 0.0 || hit0 > sceneDistance + width) return;
    vec3 diff0 = ray * hit0 - midPt;
    if (dot(diff0, diff0) > width * width * 5.2) return;

    float span = sqrt(span2);
    vec3 tangent = segment / span;
    vec3 side = cross(tangent, abs(tangent.y) > 0.96 ? vec3(1,0,0) : vec3(0,1,0));
    side = normalize(side);
    vec3 normal = cross(tangent, side);
    float twist = seed * 2.13 + age * (kind == 0 ? 0.85 : 2.1);
    float ct = cos(twist), st = sin(twist);
    vec3 x = side * ct + normal * st;
    vec3 y = normal * ct - side * st;
    width *= 0.94 + 0.08 * sin(age * 17.0 + seed * 4.0);
    float thickness = width * (kind == 0 ? 0.48 : (kind == 2 ? 0.65 : 0.34));
    vec3 scale = vec3(width, thickness, width);
    mat3 basis = transpose(mat3(x, y, tangent));
    vec3 r = (basis * ray) / scale, s = (basis * start) / scale, d = (basis * segment) / scale;
    float a = dot(r,r), b = dot(r,d), c = dot(d,d), e = dot(d,s), f = dot(r,s);
    float along = clamp((b * f - a * e) / max(0.00001, a * c - b * b), 0.0, 1.0);
    float hit = (f + b * along) / a;
    if (hit <= 0.0 || hit > sceneDistance) return;
    vec3 q = r * hit - s - d * along;
    float dist2 = dot(q,q);
    if (dist2 > 7.5) return;
    vec3 p = mix(start, end, along) - body;
    float turbulence = sin(dot(p, vec3(14,9,11)) - age * 12.0 + sin(p.y * 17.0 + seed) * 0.75);
    float vBase = 0.5 + 0.5 * sin(dot(p, vec3(23,19,7)) + turbulence * 1.5 - age * 7.5);
    float v2 = vBase * vBase; float v4 = v2 * v2; float vein = v4 * v4 * v4;
    float pBase = max(0.0, sin(p.y * 6.0 - age * 9.5 + seed));
    float p2 = pBase * pBase; float p4 = p2 * p2; float pulse = p4 * p4 * p2;
    float membrane = exp(-dist2 * 1.65);
    float core = exp(-dist2 * 5.8);
    float rimDiff = (sqrt(dist2) - 0.85) / 0.25;
    float rim = exp(-rimDiff * rimDiff);
    float visible = smoothstep(0.0, max(0.015, width * 0.45), sceneDistance - hit) * min(alpha, 1.35);
    // Saturated crimson-to-ruby liquid core with dark coagulated outer meniscus and hot arterial pulses.
    vec3 liquid = mix(GHOUL_DEEP, GHOUL_RED, clamp(core * 0.92 + 0.16 * turbulence, 0.0, 1.0));
    liquid = mix(liquid, GHOUL_RUBY, core * (0.45 + pulse * 0.35));
    float coverage = min(1.0, membrane * visible * (kind == 1 ? 0.78 : (kind == 2 ? 0.84 : 0.72)));
    if (coverage > chipAlpha) {
        chipColor = liquid * (1.0 - vein * 0.42) * coverage;
        chipAlpha = coverage;
    }
    darkness = max(darkness, rim * visible * 0.16);
    float ingDiff = (p.y - 0.9 - sin(age * 14.0) * 0.55) / 0.34;
    float ingestionWave = feeding * exp(-ingDiff * ingDiff);
    light = max(light, (GHOUL_RUBY * core * (0.78 + pulse * 0.45 + ingestionWave * 0.85) * (1.0 - vein * 0.45)
           + GHOUL_BLOOD * membrane * 0.32 + GHOUL_HOT * core * (pulse * 0.22 + ingestionWave * 0.28)) * visible);
}

// Compact volumetric energy knot. Used for extraction/ingestion/contact only, never an enclosing aura sphere.
void ghoulKnot(vec3 ray, float sceneDistance, vec3 centre, vec3 radii, float alpha, float age, float hot,
               inout vec3 light, inout float darkness) {
    if (alpha <= 0.001 || !intersectsBound(ray, sceneDistance, centre, max(radii.x, max(radii.y, radii.z)) * 1.6)) return;
    vec3 r = ray / radii, o = centre / radii;
    float a = dot(r,r), along = dot(r,o), disc = along * along - a * (dot(o,o) - 1.0);
    if (disc <= 0.0) return;
    float front = (along - sqrt(disc)) / a;
    if (front <= 0.0 || front > sceneDistance) return;
    vec3 p = (ray * front - centre) / radii;
    float invF = 1.0 - abs(dot(normalize(p / radii), ray));
    float fresnel = invF * invF;
    float churn = 0.6 + 0.4 * sin(p.x * 13.0 + age * 22.0) * sin(p.y * 11.0 - p.z * 7.0 - age * 17.0);
    float ch2 = churn * churn; float ch5 = ch2 * ch2 * churn;
    float visible = smoothstep(0.0, 0.035, sceneDistance - front) * alpha;
    light += (GHOUL_RUBY * (0.45 + fresnel * 0.65 + churn * 0.28) + GHOUL_HOT * hot * ch5 * 0.48) * visible;
    darkness += (1.0 - fresnel) * visible * 0.22;
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
    float aura = auraState.z;
    float swing = auraState.w;
    float height = max(1.2, body.w);

    // 1. Volumetric crimson torso mist + arterial floor pulse ring & orbiting 3D blood droplets during Bloodlust
    float mistAlpha = max(aura, lunge.w * 0.65) * mix(1.0, 0.35, inside);
    if (mistAlpha > 0.005 && intersectsBound(ray, sceneDistance, body.xyz + vec3(0.0, height * 0.55, 0.0), 1.65)) {
        vec3 a = body.xyz + vec3(0.0, 0.14, 0.0);
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
        // Crimson arterial floor pulse ring on activation and heartbeat
        if (auraState.x >= 0.0 && auraState.x < 0.45) {
            float ringEnv = (1.0 - smoothstep(0.08, 0.45, auraState.x)) * mistAlpha;
            goldenFloorRing(ray, sceneDistance, body.xyz + vec3(0.0, 0.04, 0.0),
                            0.25 + auraState.x * 2.8, 0.065, ringEnv * 0.75, GHOUL_RUBY, light);
        }
        // Orbiting & rising 3D crimson blood droplets / shards around the Vampire
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
            vec3 dPos = body.xyz + vec3(cos(ang) * rad, y, sin(ang) * rad);
            float sz = (0.022 + 0.015 * h1) * (0.45 + 0.55 * env) * (1.0 + feeding * 0.25);
            bloodCube(ray, sceneDistance, dPos, vec3(sz, sz * 1.45, sz), mistAlpha * env * 0.95, light, chipAlpha, chipColor);
        }
    }

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

    // 3. Blood Siphon extraction & ingestion knots + chest absorption ring
    for (int i = 0; i < 2; i++) {
        vec4 extraction = QuakeMaterials[material + 5 + i];
        vec4 ingestion = QuakeMaterials[material + 7 + i];
        ghoulKnot(ray, sceneDistance, extraction.xyz, vec3(0.12, 0.16, 0.11), extraction.w * 1.2, age, 0.45, light, darkness);
        ghoulKnot(ray, sceneDistance, ingestion.xyz, vec3(0.16, 0.25, 0.15), ingestion.w * 1.1, age, 0.35, light, darkness);
    }

    // 4. Blood Lunge launch shockwave, shedding wake droplets, and confirmed impact rupture
    if (lunge.x >= 0.0 && lunge.x < 0.82) {
        float lYaw = launch.w;
        vec3 lDir = vec3(cos(lYaw), 0.0, sin(lYaw));
        vec3 lSide = vec3(-lDir.z, 0.0, lDir.x);
        if (lunge.x < 0.32) {
            float lEnv = 1.0 - smoothstep(0.05, 0.32, lunge.x);
            goldenFloorRing(ray, sceneDistance, launch.xyz + vec3(0.0, 0.05, 0.0),
                            0.22 + (1.0 - exp(-lunge.x * 12.0)) * 1.35, 0.06, lEnv * 0.72, GHOUL_RUBY, light);
        }
        // Shedding blood droplets along the dash wake
        int wakeDrops = DepthConfig.z < 0.5 ? 3 : 6;
        for (int k = 0; k < 6; k++) {
            if (k >= wakeDrops) break;
            float fk = float(k);
            float local = lunge.x - fk * 0.032;
            if (local <= 0.0 || local > 0.36) continue;
            float sideSgn = mod(fk, 2.0) * 2.0 - 1.0;
            vec3 dOrigin = mix(launch.xyz, body.xyz, clamp(fk / 6.0, 0.0, 1.0)) + vec3(0.0, height * (0.38 + 0.08 * mod(fk, 3.0)), 0.0);
            vec3 dPos = dOrigin + lSide * sideSgn * (0.22 + local * 0.85) - lDir * local * 0.55 - vec3(0.0, local * local * 3.2, 0.0);
            float fade = sin(local / 0.36 * 3.14159);
            bloodCube(ray, sceneDistance, dPos, vec3(0.024, 0.034, 0.024), fade * 0.9, light, chipAlpha, chipColor);
        }
    }

    if (lunge.y > 0.5 && lunge.x >= 0.055 && lunge.x < 0.48) {
        float t = lunge.x - 0.055;
        ghoulKnot(ray, sceneDistance, contact.xyz, vec3(0.17, 0.27, 0.17) * (1.0 + t * 3.2),
                  contact.w * 1.45, age, 1.0, light, darkness);
        if (intersectsBound(ray, sceneDistance, contact.xyz, 1.85)) {
            float lYaw = launch.w;
            vec3 A = vec3(-cos(lYaw), 0.0, -sin(lYaw));
            vec3 up = vec3(0.0, 1.0, 0.0);
            vec3 side = vec3(-A.z, 0.0, A.x);
            // Twin cross-slash crimson blades across the victim
            float cutAlpha = (1.0 - smoothstep(0.08, 0.32, t));
            float reach = 0.88 * smoothstep(0.0, 0.055, t);
            if (cutAlpha > 0.001) {
                vec3 X1 = normalize(side * 0.78 - up * 0.62);
                vec3 Y1 = normalize(cross(A, X1));
                critCut(ray, sceneDistance, contact.xyz, mat3(X1, Y1, A), vec3(reach, 0.032, 0.11),
                        cutAlpha, light, chipAlpha, chipColor);
                vec3 X2 = normalize(side * 0.78 + up * 0.62);
                vec3 Y2 = normalize(cross(A, X2));
                critCut(ray, sceneDistance, contact.xyz, mat3(X2, Y2, A), vec3(reach * 0.82, 0.026, 0.11),
                        cutAlpha * 0.88, light, chipAlpha, chipColor);
            }
            // Spatial impact lens + bursting 3D blood drops
            float along = dot(ray, contact.xyz);
            if (along > 0.0 && along < sceneDistance) {
                float d = length(ray * along - contact.xyz);
                float lens = exp(-d * d / (0.18 + t * 0.9)) * cutAlpha * 0.55;
                vec2 proj = texCoord - screenPosition(contact.xyz);
                distortion += proj / max(length(proj), 0.001) * lens;
            }
            int burstDrops = DepthConfig.z < 0.5 ? 4 : (DepthConfig.z < 1.5 ? 6 : 8);
            for (int k = 0; k < 8; k++) {
                if (k >= burstDrops) break;
                float f = float(k), a = f * 2.39996 + QuakeMaterials[material + 9].y;
                float seed = fract(f * 0.618034 + 0.31);
                vec3 speed = vec3(cos(a) * (1.8 + seed * 1.4), 1.1 + seed * 1.2, sin(a) * (1.8 + seed * 1.4));
                vec3 p = contact.xyz + speed * (1.0 - exp(-t * 4.8)) / 4.8 - vec3(0.0, t * t * 4.2, 0.0);
                float dFade = 1.0 - smoothstep(0.14, 0.42, t);
                float sz = 0.022 + 0.014 * seed;
                bloodCube(ray, sceneDistance, p, vec3(sz, sz * 1.35, sz), dFade, light, chipAlpha, chipColor);
            }
        }
    }
}

void main() {
    vec4 original = texture(InSampler, texCoord);
    if (WaveStyles[0].x + WaveStyles[1].x + WaveStyles[2].x + WaveStyles[3].x + ImpactConfig.y <= 0.0) {
        fragColor = original;
        return;
    }
    float depth = texture(DepthSampler, texCoord).r;
    // Reverse-Z: sky is ZERO, near plane is ONE. The old shader assumed the opposite.
    vec3 ray = normalize(unproject(0.5));
    vec3 scenePoint = depth <= 0.000001 ? vec3(0.0) : unproject(depth);
    float sceneDistance = depth <= 0.000001 ? 1e6 : length(scenePoint);
    vec3 light = vec3(0.0);
    float tint = 0.0;
    float chipAlpha = 0.0;
    float darkness = 0.0;
    float maskStrength = 0.0;
    float maskCrystal = 0.0;
    float critAlpha = 0.0;
    float critAge = 0.0;
    vec2 critUv = vec2(0.5);
    float maskAge = 0.0;
    float maskScale = 1.0;
    float maskBurst = 0.0;
    vec3 maskParams = vec3(1.0);
    float bulwarkStrength = 0.0;
    float bulwarkAge = 0.0;
    float bulwarkScale = 1.0;
    float bulwarkPulse = 0.0;
    float bulwarkAbsorb = 0.0;
    float ghoulStrength = 0.0;
    float ghoulAge = 0.0;
    float ghoulScale = 1.0;
    float ghoulBurst = 0.0;
    float ghoulHeartbeat = 0.0;
    vec3 chipColor = vec3(0.0);
    vec2 distortion = vec2(0.0);

    for (int i = 0; i < 4; i++) {
        float alpha = WaveStyles[i].x;
        if (alpha <= 0.0) continue;
        vec3 origin = WaveOrigins[i].xyz;
        float radius = WaveOrigins[i].w;
        float age = WaveStyles[i].y;
        float charging = WaveStyles[i].z;
        if (charging > 11.5) {
            if (WaveStyles[i].w > 0.0) {
                ghoulStrength = max(ghoulStrength, WaveStyles[i].w);
                ghoulAge = age;
                ghoulScale = QuakeMaterials[i * 32 + 9].w;
                ghoulBurst = max(ghoulBurst, QuakeMaterials[i * 32 + 10].x);
                ghoulHeartbeat = max(ghoulHeartbeat, QuakeMaterials[i * 32 + 10].y);
            }
            ghoulBlood(ray, sceneDistance, i, light, darkness, chipAlpha, chipColor, distortion);
            continue;
        }
        if (charging > 9.5) {
            bool owner = charging > 10.5;
            if (owner) {
                bulwarkStrength = max(bulwarkStrength, alpha);
                bulwarkAge = age;
                bulwarkScale = WaveStyles[i].w;
                bulwarkPulse = max(bulwarkPulse, QuakeMaterials[i * 32].y);
                bulwarkAbsorb = max(bulwarkAbsorb, QuakeMaterials[i * 32].x);
            }
            bulwarkAura(ray, sceneDistance, i, owner, light, darkness, chipAlpha, chipColor, distortion);
            continue;
        }
        if (charging > 8.5) {
            shieldRam(ray, sceneDistance, i, light, darkness, chipAlpha, chipColor, distortion);
            continue;
        }
        if (charging > 7.5) {
            critAlpha = max(critAlpha, alpha);
            critAge = age;
            float critAspect = float(textureSize(InSampler, 0).x) / float(textureSize(InSampler, 0).y);
            vec4 clip = ViewProjection * vec4(origin, 1.0);
            vec2 hitUv = clip.w > 0.0001 ? clip.xy / clip.w * 0.5 + 0.5 : vec2(0.5);
            critUv = (clamp(hitUv, vec2(0.10), vec2(0.90)) - 0.5) * vec2(critAspect, 1.0);
            critBurst(ray, sceneDistance, i, light, darkness, chipAlpha, chipColor, distortion);
            continue;
        }
        if (charging > 6.5) {
            crystalAura(ray, sceneDistance, i, light, darkness, chipAlpha, chipColor);
            continue;
        }
        if (charging > 5.5) {
            maskStrength = max(maskStrength, alpha);
            maskCrystal = 1.0;
            maskAge = WaveStyles[i].y;
            maskScale = WaveStyles[i].w;
            maskBurst = QuakeMaterials[i * 32].x; // activation prismatic burst 0..1
            maskParams = WaveOrigins[i].xyz; // density, noise, distortion (client settings, 1.0 = default)
            crystalAura(ray, sceneDistance, i, light, darkness, chipAlpha, chipColor);
            continue;
        }
        if (charging > 4.5) {
            maskAura(ray, sceneDistance, i, light, darkness, chipAlpha, chipColor);
            continue;
        }
        if (charging > 3.5) {
            maskStrength = max(maskStrength, alpha);
            maskAge = WaveStyles[i].y;
            maskScale = WaveStyles[i].w;
            maskBurst = QuakeMaterials[i * 32].x; // activation chromatic burst 0..1
            maskParams = WaveOrigins[i].xyz; // density, noise, distortion (client settings, 1.0 = default)
            maskAura(ray, sceneDistance, i, light, darkness, chipAlpha, chipColor);
            continue;
        }
        if (charging > 2.5) {
            darkBolt(ray, sceneDistance, i, light, darkness, distortion);
            continue;
        }
        if (charging > 1.5) {
            electricQuake(ray, sceneDistance, scenePoint, i, light, tint, chipAlpha, chipColor);
            continue;
        }
        if (!intersectsBound(ray, sceneDistance, origin, charging > 0.5 ? radius * 1.15 + 0.2 : 6.5)) continue;
        if (charging < 0.5) fragments(ray, sceneDistance, i, light);
        // Ejecta outlive the membrane, without keeping an almost-transparent sphere alive.
        if (alpha < 0.005) continue;
        shell(ray, sceneDistance, origin, radius, alpha, age, 1.0, charging, light, tint, distortion);
        corona(ray, sceneDistance, origin, radius, alpha, light);
        if (charging > 0.5) {
            // Dense luminous core and a translucent outer shell during accumulation.
            float core = radius * 0.26;
            shell(ray, sceneDistance, origin, core, alpha * 0.75, age, 0.0, 1.0, light, tint, distortion);
            singularity(ray, sceneDistance, origin, WaveStyles[i].w, alpha, light, distortion);
            continue;
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

    vec2 pixel = 1.0 / vec2(textureSize(InSampler, 0));
    vec2 split = distortion * pixel * 7.0;
    vec2 pressure = vec2(0.0);
    if (ImpactConfig.y > 0.0) {
        float aspect = float(textureSize(InSampler, 0).x) / float(textureSize(InSampler, 0).y);
        vec2 radial = texCoord - ImpactConfig.zw;
        vec2 metric = radial * vec2(aspect, 1.0);
        float radius = length(metric);
        float edge = smoothstep(0.0, 0.75, radius);
        // Short spatial pressure wave and damped recoil; no flash, blur, noise or camera shake.
        float wave = sin(radius * 12.0 - ImpactConfig.y * 4.0) * 0.22 + 0.78;
        pressure = radial * ImpactConfig.x * (0.018 + 0.010 * wave);
        split += radial * ImpactConfig.y * edge * 0.005;
    }
    float aspectRatio = float(textureSize(InSampler, 0).x) / float(textureSize(InSampler, 0).y);
    vec2 maskUv = (texCoord - 0.5) * vec2(aspectRatio, 1.0);
    float maskField = 0.0;
    if (maskStrength > 0.0) {
        // Large slow noise drives a gentle, smooth warp (no chromatic split, no blur).
        float s = maskStrength * maskScale;
        vec2 q = maskUv * 2.2 + vec2(maskAge * 0.07, -maskAge * 0.05);
        float n1 = softNoise(q) - 0.5, n2 = softNoise(q + vec2(5.2, 1.3) + maskAge * 0.04) - 0.5;
        // Two octaves: broad swell plus a finer ripple; stronger toward the edges, calm in the centre.
        vec2 q2 = maskUv * 5.0 + vec2(-maskAge * 0.11, maskAge * 0.09);
        vec2 ripple = vec2(sin(q2.x + sin(q2.y * 1.3 + maskAge * 0.5)), sin(q2.y * 1.1 + sin(q2.x * 0.9 - maskAge * 0.4))) * 0.5;
        pressure += (vec2(n1, n2) * 0.011 + ripple * 0.0045) * s * maskParams.z * smoothstep(0.05, 0.7, length(maskUv));
        maskField = 0.5 + 0.5 * sin(dot(maskUv, vec2(0.8, 0.6)) * 5.5 + n1 * 5.0 - maskAge * 0.55);
    }
    if (maskBurst > 0.001) {
        // Radial RGB split punching outward from the screen centre; strongest at the edges, fades with the burst.
        vec2 radialBurst = texCoord - 0.5;
        split += radialBurst * (0.35 + length(radialBurst) * 1.4) * maskBurst * 0.05 * min(maskScale, 1.5);
    }
    if (ghoulBurst > 0.001) {
        vec2 radialGhoul = texCoord - 0.5;
        split += radialGhoul * (0.28 + length(radialGhoul) * 1.15) * ghoulBurst * 0.032 * min(ghoulScale, 1.5);
    }
    if (critAlpha > 0.0) {
        // Very short impact recoil: a few frames of radial pressure, no lingering screen tint.
        float flash = exp(-critAge * 30.0) * min(critAlpha * 2.0, 1.0);
        vec2 push = (texCoord - 0.5) * vec2(aspectRatio, 1.0) - critUv;
        pressure += push * flash * 0.016;
        split += push * flash * 0.005;
    }
    vec2 lower = pixel * 0.5;
    vec2 upper = vec2(1.0) - lower;
    // Refraction and RGB separation are confined to the expanding shell's rim.
    vec2 uv = texCoord + pressure + split * 0.55;
    vec3 refracted = original.rgb;
    if (dot(split, split) < 1e-12 && dot(pressure, pressure) > 1e-12) {
        refracted = texture(InSampler, clamp(uv, lower, upper)).rgb; // pure warp: a single tap
    } else if (dot(split, split) + dot(pressure, pressure) > 1e-12) {
        refracted = vec3(
            texture(InSampler, clamp(uv + split, lower, upper)).r,
            texture(InSampler, clamp(uv, lower, upper)).g,
            texture(InSampler, clamp(uv - split, lower, upper)).b
        );
    }
    vec3 color = mix(refracted, CYAN, min(tint, 0.68)) * (1.0 - min(darkness, 0.9)) + min(light, vec3(0.85));
    color = mix(color, chipColor / max(chipAlpha, 0.00001), min(chipAlpha, 1.0));
    if (maskStrength > 0.0) {
        float s = maskStrength * maskScale;
        float dens = maskParams.x, noiseAmt = maskParams.y;
        float radius = length(maskUv);
        float vignette = smoothstep(0.30, 1.15, radius);
        // Two grain scales (broad clouds + medium flecks), slowly drifting; never per-pixel digital noise.
        float grain = (softNoise(maskUv * 6.0 + vec2(maskAge * 0.35, -maskAge * 0.2)) - 0.5) * 0.65
                    + (softNoise(maskUv * 17.0 + vec2(-maskAge * 0.6, maskAge * 0.45)) - 0.5) * 0.35;
        float luma = dot(color, vec3(0.299, 0.587, 0.114));
        // Matadora keeps its predatory violet; Crystal Claw desaturates less and cools the tint toward glacier blue.
        vec3 tintMul = mix(vec3(0.66, 0.50, 0.96), vec3(0.58, 0.86, 1.02), maskCrystal);
        vec3 tintAdd = mix(vec3(0.03, 0.0, 0.07), vec3(0.0, 0.045, 0.085), maskCrystal);
        vec3 edgeTint = mix(VOID_VIOLET, CRYSTAL_TINT, maskCrystal);
        float desat = mix(0.20, 0.10, maskCrystal);
        color = mix(color, vec3(luma), min(desat * s * dens, 0.5));                          // less saturated, predatory
        color = mix(color, color * tintMul + tintAdd, min((0.32 + 0.12 * maskField) * s * dens, 0.7));
        color += edgeTint * vignette * (0.10 + 0.08 * maskField) * s * dens;                  // edge glow
        color *= 1.0 - min(vignette * mix(0.42, 0.34, maskCrystal) * s * dens + 0.03 * s * dens, 0.65);
        color += grain * mix(0.11, 0.075, maskCrystal) * s * noiseAmt;                        // large-scale grain
    }
    if (bulwarkStrength > 0.0) {
        float s = bulwarkStrength * bulwarkScale;
        vec2 guardUv = (texCoord - 0.5) * vec2(aspectRatio, 1.0);
        float radius = length(guardUv);
        float edge = smoothstep(0.30, 1.12, radius);
        vec3 plate = mix(BASTION_GOLD, BASTION_PALE, bulwarkAbsorb * 0.5);
        color += plate * edge * (0.018 + 0.004 * sin(bulwarkAge * 2.4)) * s;
        color += BASTION_WHITE * bulwarkPulse * edge * 0.035 * s;
    }
    if (ghoulStrength > 0.0) {
        float s = ghoulStrength * ghoulScale;
        vec2 bloodUv = (texCoord - 0.5) * vec2(aspectRatio, 1.0);
        float radius = length(bloodUv);
        float edge = smoothstep(0.28, 1.10, radius);
        float vein = 0.5 + 0.5 * sin(atan(bloodUv.y, bloodUv.x) * 7.0 + sin(radius * 12.0 - ghoulAge * 4.5));
        float pulse = 0.032 + 0.024 * ghoulHeartbeat + 0.035 * ghoulBurst;
        color *= 1.0 - min(edge * 0.24 * s, 0.45);
        color += mix(GHOUL_BLOOD, GHOUL_RUBY, 0.45 + 0.35 * vein) * edge * pulse * s;
    }
    fragColor = vec4(color, original.a);
}
