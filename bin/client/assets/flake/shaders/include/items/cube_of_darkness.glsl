// Active Item: Cube of Darkness ("Куб темряви")

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
