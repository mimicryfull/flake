// Weapon: Void Paladin ("Порожній Паладин") — 4th-hit critical slash & falling blood droplets

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
