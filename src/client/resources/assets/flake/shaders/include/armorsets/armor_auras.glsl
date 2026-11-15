// Custom Iron-tier armor set VFX:
// - 27.0: Phoenix Aura (yellow-orange pulsing ground rings & gentle underglow, non-obstructive)
// - 28.0: Barrel Pulse (expanding dark-brown ring wave with earthy distortion)
// - 29.0: Hydra Aura (crimson & dark-red pulsing ground aura & crimson rim shadow)

const vec3 PHOENIX_GOLD = vec3(1.00, 0.78, 0.14);
const vec3 PHOENIX_ORANGE = vec3(1.00, 0.42, 0.05);
const vec3 PHOENIX_EMBER_CORE = vec3(1.00, 0.94, 0.56);

const vec3 BARREL_DARK = vec3(0.32, 0.16, 0.05);
const vec3 BARREL_OAK = vec3(0.62, 0.34, 0.11);
const vec3 BARREL_RIM = vec3(0.84, 0.56, 0.24);

const vec3 HYDRA_DARK_RED = vec3(0.44, 0.02, 0.07);
const vec3 HYDRA_CRIMSON = vec3(0.86, 0.08, 0.16);
const vec3 HYDRA_SCARLET = vec3(1.00, 0.24, 0.24);

void phoenixAuraWave(vec3 ray, float sceneDistance, vec3 scenePoint, float depth, int slot,
                     inout vec3 light, inout float darkness) {
    float alpha = WaveStyles[slot].x;
    float age = WaveStyles[slot].y;
    float radius = max(1.0, WaveOrigins[slot].w);
    float empowered = WaveStyles[slot].w; // 0 = normal (2 dmg), 1 = low HP (4 dmg)
    vec3 groundOrigin = WaveOrigins[slot].xyz;

    float cycle = mix(0.95, 1.35, empowered);
    float beat1 = fract(age * cycle);
    float beat2 = fract(age * cycle + 0.50);
    float intensity = mix(0.62, 0.92, empowered) * alpha;

    // Gentle Shaman-style ground-projected yellow-orange pulsing rings (non-obstructive).
    shamanSceneRing(scenePoint, depth, groundOrigin,
                    mix(0.35, radius * 0.96, beat1), 0.22, (1.0 - beat1) * 0.68 * intensity, PHOENIX_EMBER_CORE, light);
    shamanSceneRing(scenePoint, depth, groundOrigin,
                    mix(0.35, radius * 0.96, beat2), 0.18, (1.0 - beat2) * 0.48 * intensity, PHOENIX_ORANGE, light);
    shamanSceneRing(scenePoint, depth, groundOrigin, radius,
                    0.18, (0.50 + 0.16 * sin(age * 4.5)) * intensity, PHOENIX_GOLD, light);

    if (depth > 0.000001) {
        vec3 rel = scenePoint - groundOrigin;
        float rXZ = length(rel.xz);
        if (rXZ < radius && abs(rel.y) < 1.35) {
            float pulse = 0.76 + 0.24 * sin(age * 5.2);
            float centerPool = exp(-(rXZ * rXZ) / 2.2) * 0.22;
            float ringFade = smoothstep(radius, radius * 0.55, rXZ) * 0.10;
            float yMask = exp(-rel.y * rel.y * 2.4);
            light += mix(PHOENIX_ORANGE, PHOENIX_GOLD, exp(-(rXZ * rXZ) / 1.1))
                     * (centerPool + ringFade) * yMask * pulse * intensity;
        }
    }
}

void barrelPulseWave(vec3 ray, float sceneDistance, vec3 scenePoint, float depth, int slot,
                     inout vec3 light, inout float darkness, inout vec2 distortion) {
    float alpha = WaveStyles[slot].x;
    float age = WaveStyles[slot].y;
    float radius = max(1.0, WaveOrigins[slot].w);
    vec3 groundOrigin = WaveOrigins[slot].xyz;

    float t = clamp(age / 0.65, 0.0, 1.0);
    float ringR = mix(0.35, radius, 1.0 - pow(1.0 - t, 2.2));
    float fade = alpha * (1.0 - t);

    shamanSceneRing(scenePoint, depth, groundOrigin, ringR, 0.34, fade * 0.95, BARREL_RIM, light);
    shamanSceneRing(scenePoint, depth, groundOrigin, ringR * 0.72, 0.26, fade * 0.75, BARREL_OAK, light);

    if (depth > 0.000001) {
        vec3 rel = scenePoint - groundOrigin;
        float rXZ = length(rel.xz);
        if (rXZ < radius && abs(rel.y) < 1.5) {
            float band = exp(-pow(rXZ - ringR, 2.0) / 0.28);
            float yMask = exp(-rel.y * rel.y * 2.2);
            light += mix(BARREL_DARK, BARREL_OAK, band) * band * yMask * fade * 0.9;
            darkness = max(darkness, band * yMask * fade * 0.35);
            vec2 dir2 = length(rel.xz) > 0.001 ? normalize(rel.xz) : vec2(0.0);
            distortion += dir2 * band * yMask * fade * 0.32;
        }
    }
}

void hydraAuraWave(vec3 ray, float sceneDistance, vec3 scenePoint, float depth, int slot,
                   inout vec3 light, inout float darkness) {
    float alpha = WaveStyles[slot].x;
    float age = WaveStyles[slot].y;
    float radius = max(1.0, WaveOrigins[slot].w);
    float frenzy = WaveStyles[slot].w; // 0 = passive (+20%), 1 = enraged (+40% at <=90% HP)
    vec3 groundOrigin = WaveOrigins[slot].xyz;

    float cycle = mix(0.68, 1.30, frenzy);
    float beat1 = fract(age * cycle);
    float beat2 = fract(age * cycle + 0.50);
    float intensity = mix(0.34, 0.92, frenzy) * alpha;

    shamanSceneRing(scenePoint, depth, groundOrigin,
                    mix(0.28, radius, beat1), 0.20, (1.0 - beat1) * 0.75 * intensity, HYDRA_SCARLET, light);
    shamanSceneRing(scenePoint, depth, groundOrigin,
                    mix(0.28, radius, beat2), 0.18, (1.0 - beat2) * 0.50 * intensity, HYDRA_CRIMSON, light);
    shamanSceneRing(scenePoint, depth, groundOrigin, radius,
                    0.16, (0.45 + 0.20 * sin(age * 4.8)) * intensity, HYDRA_DARK_RED, light);

    if (depth > 0.000001) {
        vec3 rel = scenePoint - groundOrigin;
        float rXZ = length(rel.xz);
        if (rXZ < radius && abs(rel.y) < 1.45) {
            float heart = 0.72 + 0.28 * sin(age * 6.0);
            float centerPool = exp(-(rXZ * rXZ) / 1.45) * 0.28;
            float yMask = exp(-rel.y * rel.y * 2.1);
            light += mix(HYDRA_DARK_RED, HYDRA_CRIMSON, exp(-(rXZ * rXZ) / 0.85))
                     * centerPool * yMask * heart * intensity;
            if (frenzy > 0.5) {
                darkness = max(darkness, smoothstep(radius, radius * 0.2, rXZ) * yMask * 0.18 * alpha);
            }
        }
    }
}
