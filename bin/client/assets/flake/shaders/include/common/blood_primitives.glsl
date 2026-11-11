// Shared 3D blood droplet and slash-blade primitives used by Vampire skills and Void Paladin crits.

const vec3 CRIMSON = vec3(0.44, 0.015, 0.055);
const vec3 CRIMSON_HOT = vec3(0.86, 0.14, 0.16);
const vec3 CRIMSON_WHITE = vec3(1.0, 0.58, 0.55);
const vec3 BLOOD_DARK = vec3(0.16, 0.002, 0.02);

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
