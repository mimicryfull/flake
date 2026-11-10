// Class: Vampire ("Упир") — Skill 1: Blood Lunge ("Кривавий ривок")

void ghoulBloodLunge(vec3 ray, float sceneDistance, int material, float age, float height, vec3 bodyPos,
                     vec4 lunge, vec4 contact, vec4 launch,
                     inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    // Launch shockwave & shedding blood droplets along the dash wake
    if (lunge.x >= 0.0 && lunge.x < 0.82) {
        float lYaw = launch.w;
        vec3 lDir = vec3(cos(lYaw), 0.0, sin(lYaw));
        vec3 lSide = vec3(-lDir.z, 0.0, lDir.x);
        if (lunge.x < 0.32) {
            float lEnv = 1.0 - smoothstep(0.05, 0.32, lunge.x);
            goldenFloorRing(ray, sceneDistance, launch.xyz + vec3(0.0, 0.05, 0.0),
                            0.22 + (1.0 - exp(-lunge.x * 12.0)) * 1.35, 0.06, lEnv * 0.72, GHOUL_RUBY, light);
        }
        int wakeDrops = DepthConfig.z < 0.5 ? 3 : 6;
        for (int k = 0; k < 6; k++) {
            if (k >= wakeDrops) break;
            float fk = float(k);
            float local = lunge.x - fk * 0.032;
            if (local <= 0.0 || local > 0.36) continue;
            float sideSgn = mod(fk, 2.0) * 2.0 - 1.0;
            vec3 dOrigin = mix(launch.xyz, bodyPos, clamp(fk / 6.0, 0.0, 1.0)) + vec3(0.0, height * (0.38 + 0.08 * mod(fk, 3.0)), 0.0);
            vec3 dPos = dOrigin + lSide * sideSgn * (0.22 + local * 0.85) - lDir * local * 0.55 - vec3(0.0, local * local * 3.2, 0.0);
            float fade = sin(local / 0.36 * 3.14159);
            bloodCube(ray, sceneDistance, dPos, vec3(0.024, 0.034, 0.024), fade * 0.9, light, chipAlpha, chipColor);
        }
    }

    // Confirmed hit climax: twin cross-slash blades, spatial lens, and ballistic blood burst
    if (lunge.y > 0.5 && lunge.x >= 0.055 && lunge.x < 0.48) {
        float t = lunge.x - 0.055;
        ghoulKnot(ray, sceneDistance, contact.xyz, vec3(0.17, 0.27, 0.17) * (1.0 + t * 3.2),
                  contact.w * 1.45, age, 1.0, light, darkness);
        if (intersectsBound(ray, sceneDistance, contact.xyz, 1.85)) {
            float lYaw = launch.w;
            vec3 A = vec3(-cos(lYaw), 0.0, -sin(lYaw));
            vec3 up = vec3(0.0, 1.0, 0.0);
            vec3 side = vec3(-A.z, 0.0, A.x);
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
