// Class: Vampire ("Упир") — Shared blood ribbon & volumetric knot primitives

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
