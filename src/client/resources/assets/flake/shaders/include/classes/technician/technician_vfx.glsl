// Class: Technician ("Технік") — Oreshnik homing missile plume, Strike UAV laser designator & multi-layer blasts.

const vec3 TECH_FIRE_CORE  = vec3(1.00, 0.96, 0.72);
const vec3 TECH_FIRE_AMBER = vec3(1.00, 0.62, 0.14);
const vec3 TECH_FIRE_RED   = vec3(0.96, 0.24, 0.06);
const vec3 TECH_LASER_CYAN = vec3(0.18, 0.96, 0.92);

// Style 25: Technician multiplexed VFX (mode 0 = Missile, mode 1 = UAV Laser/Beacon, mode 2 = Detonation Blast).
void technicianWave(vec3 ray, float sceneDistance, vec3 scenePoint, float depth, int slotIndex,
                    inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    float alpha = WaveStyles[slotIndex].x;
    float age = WaveStyles[slotIndex].y;
    float mode = WaveStyles[slotIndex].w;
    vec3 origin = WaveOrigins[slotIndex].xyz;
    float radius = max(WaveOrigins[slotIndex].w, 1.5);
    if (!intersectsBound(ray, sceneDistance, origin, radius + 3.0)) return;

    int segBase = slotIndex * 64;

    if (mode < 0.5) {
        // Mode 0: Oreshnik rocket engine plasma flame + incandescent contrail + target lock ring
        vfxPointFlash(ray, sceneDistance, origin, 1.45, alpha * 1.15, TECH_FIRE_CORE, TECH_FIRE_AMBER,
                      light, chipAlpha, chipColor);

        int count = clamp(int(QuakeMaterials[slotIndex * 32].x + 0.5), 0, 24);
        for (int s = 0; s < count; s++) {
            vec3 a = QuakeStarts[segBase + s].xyz;
            vec3 b = QuakeEnds[segBase + s].xyz;
            float fade = clamp(QuakeStarts[segBase + s].w, 0.0, 1.0);
            float rDist, segT, perp;
            vfxClosestRaySegment(ray, a, b, rDist, segT, perp);
            if (rDist > sceneDistance + 0.25) continue;

            float w = mix(0.055, 0.14, 1.0 - fade);
            float core = exp(-(perp * perp) / (w * w));
            float halo = exp(-(perp * perp) / (w * w * 9.0));
            vec3 col = mix(TECH_FIRE_RED, TECH_FIRE_CORE, fade * 0.85);
            light += (col * halo * 0.55 + TECH_FIRE_CORE * core * 0.65) * fade * alpha;
            float c = core * fade * alpha * 0.60;
            chipColor += TECH_FIRE_CORE * c;
            chipAlpha += c;
        }

        if (QuakeMaterials[slotIndex * 32].y > 0.5) {
            vec3 targetGround = QuakeEnds[segBase + 31].xyz;
            float lockR = 0.85 + 0.15 * sin(age * 16.0);
            shamanSceneRing(scenePoint, depth, targetGround, lockR, 0.14, alpha * 0.72, TECH_FIRE_AMBER, light);
        }

        float along = dot(ray, origin);
        if (along > 0.0 && along < sceneDistance + 0.5) {
            vec2 delta = screenPosition(origin) - texCoord;
            float dl = length(delta);
            if (dl > 0.0001 && dl < 0.14) {
                distortion += normalize(delta) * exp(-dl * dl * 120.0) * sin(age * 28.0) * alpha * 0.14;
            }
        }
        return;
    }

    if (mode < 1.5) {
        // Mode 1: Strike UAV engine strobe + optical laser designator beam + ground target zone
        float pulse = 0.72 + 0.28 * sin(age * 14.0);
        vfxPointFlash(ray, sceneDistance, origin, 0.95, alpha * 0.70 * pulse, TECH_LASER_CYAN, TECH_FIRE_AMBER,
                      light, chipAlpha, chipColor);

        if (QuakeMaterials[slotIndex * 32].x > 0.5) {
            vec3 beamStart = QuakeStarts[segBase].xyz;
            vec3 lockGround = QuakeEnds[segBase].xyz;
            float rDist, segT, perp;
            vfxClosestRaySegment(ray, beamStart, lockGround, rDist, segT, perp);
            if (rDist <= sceneDistance + 0.18) {
                float beamCore = exp(-(perp * perp) / 0.0009);
                float beamGlow = exp(-(perp * perp) / 0.014);
                vec3 laserCol = mix(TECH_LASER_CYAN, TECH_FIRE_RED, clamp(QuakeMaterials[slotIndex * 32].y, 0.0, 1.0));
                light += (TECH_FIRE_CORE * beamCore * 0.85 + laserCol * beamGlow * 0.42) * alpha;
            }
            float lockAlert = clamp(QuakeMaterials[slotIndex * 32].y, 0.0, 1.0);
            vec3 ringCol = mix(TECH_LASER_CYAN, TECH_FIRE_AMBER, lockAlert);
            float outerR = mix(1.55, 1.15, 0.5 + 0.5 * sin(age * 10.0));
            shamanSceneRing(scenePoint, depth, lockGround, outerR, 0.16, alpha * 0.82, ringCol, light);
            shamanSceneRing(scenePoint, depth, lockGround, 0.55, 0.11, alpha * (0.55 + 0.35 * lockAlert), TECH_FIRE_CORE, light);
        }
        return;
    }

    // Mode 2: Multi-layer concussion blast + plasma fireball + shockwaves + distortion
    float blastT = clamp(age / 0.92, 0.0, 1.0);
    float easeOut = 1.0 - pow(1.0 - blastT, 2.7);
    float fade = (1.0 - blastT) * alpha;

    // Initial white-gold core flash + dark scorched vignette contrast
    float flashEnv = exp(-age * 9.0) * alpha;
    vfxPointFlash(ray, sceneDistance, origin + vec3(0.0, 0.35, 0.0), radius * 0.85,
                  flashEnv * 1.45, TECH_FIRE_CORE, TECH_FIRE_AMBER, light, chipAlpha, chipColor);

    float fireballR = mix(0.32, radius * 0.92, easeOut);
    shamanSmoothShell(ray, sceneDistance, origin + vec3(0.0, 0.40, 0.0), fireballR, 0.24,
                      fade * 0.88, TECH_FIRE_RED, TECH_FIRE_CORE, light, distortion);

    float shockR = mix(0.35, radius * 1.30, easeOut);
    float machR  = mix(0.22, radius * 0.88, easeOut);
    shamanSceneRing(scenePoint, depth, origin, shockR, 0.30, fade * 0.95, TECH_FIRE_RED, light);
    shamanSceneRing(scenePoint, depth, origin, machR, 0.20, exp(-age * 5.2) * alpha * 0.95, TECH_FIRE_CORE, light);

    float bAlong = dot(ray, origin);
    if (bAlong > 0.0 && bAlong < sceneDistance + 1.0) {
        vec3 perpVec = ray * bAlong - origin;
        float d = length(perpVec);
        float shellBand = exp(-pow((d - shockR) / 0.42, 2.0));
        vec2 push = texCoord - screenPosition(origin);
        float pLen = length(push);
        if (pLen > 0.0005) {
            distortion += (push / pLen) * (shellBand * 0.48 + flashEnv * 0.34) * fade;
        }
        darkness += exp(-(d * d) / max(fireballR * fireballR * 0.65, 0.15)) * (1.0 - flashEnv) * fade * 0.22;
    }
}
