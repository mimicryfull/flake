// Class: Bastion ("Бастіон") — Shared shield-plate geometry, streaks and dust primitives

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
