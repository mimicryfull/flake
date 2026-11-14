#version 330
#extension GL_ARB_separate_shader_objects : require

// ---- Common Uniforms, Math & Primitives -------------------------------------
#include <flake:common/uniforms_and_math.glsl>
#include <flake:common/blood_primitives.glsl>

// ---- Class: Concussed ("Контужений") ----------------------------------------
#include <flake:classes/concussed/gravitational_dissonance.glsl>
#include <flake:classes/concussed/electric_quake.glsl>

// ---- Active Items & Weapons -------------------------------------------------
#include <flake:items/cube_of_darkness.glsl>
#include <flake:items/matadora.glsl>
#include <flake:items/crystal_claw.glsl>
#include <flake:items/screen_overlays.glsl>
#include <flake:items/chronosphere.glsl>
#include <flake:weapons/void_paladin_crit.glsl>
#include <flake:weapons/task8_weapons.glsl>

// ---- Class: Bastion ("Бастіон") ---------------------------------------------
#include <flake:classes/bastion/bastion_common.glsl>
#include <flake:classes/bastion/shield_ram.glsl>
#include <flake:classes/bastion/bulwark.glsl>

// ---- Class: Vampire ("Упир") ------------------------------------------------
#include <flake:classes/vampire/vampire_common.glsl>
#include <flake:classes/vampire/blood_lunge.glsl>
#include <flake:classes/vampire/bloodlust.glsl>
#include <flake:classes/demolitionist/demolition_blast.glsl>
#include <flake:classes/hunter_highlight.glsl>
#include <flake:classes/shaman/shaman_vfx.glsl>
#include <flake:items/vfx_beams_and_orbs.glsl>
#include <flake:classes/technician/technician_vfx.glsl>

void main() {
    vec4 original = texture(InSampler, texCoord);
    if (WaveStyles[0].x + WaveStyles[1].x + WaveStyles[2].x + WaveStyles[3].x + ImpactConfig.y <= 0.0) {
        fragColor = original;
        return;
    }
    float depth = texture(DepthSampler, texCoord).r;
    // Reverse-Z: sky is ZERO, near plane is ONE.
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
    vec3 skyOverride = vec3(0.0);
    float skyOverrideAlpha = 0.0;
    float chronoStunStrength = 0.0;
    float chronoStunAge = 0.0;

    for (int i = 0; i < 4; i++) {
        float alpha = WaveStyles[i].x;
        if (alpha <= 0.0) continue;
        vec3 origin = WaveOrigins[i].xyz;
        float age = WaveStyles[i].y;
        float charging = WaveStyles[i].z;
        if (charging > 25.5) {
            kolobokWave(ray, sceneDistance, scenePoint, depth, i, light, darkness, chipAlpha, chipColor, distortion);
            continue;
        }
        if (charging > 24.5) {
            technicianWave(ray, sceneDistance, scenePoint, depth, i, light, darkness, chipAlpha, chipColor, distortion);
            continue;
        }
        if (charging > 23.5) {
            combatDroneWave(ray, sceneDistance, scenePoint, depth, i, light, chipAlpha, chipColor, distortion);
            continue;
        }
        if (charging > 22.5) {
            sparkSyringeWave(ray, sceneDistance, i, light, chipAlpha, chipColor, distortion);
            continue;
        }
        if (charging > 21.5) {
            voidAnchorWave(ray, sceneDistance, scenePoint, depth, i, light, darkness, chipAlpha, chipColor, distortion);
            continue;
        }
        if (charging > 20.5) {
            chainLashWave(ray, sceneDistance, i, light, darkness, chipAlpha, chipColor, distortion);
            continue;
        }
        if (charging > 19.5) {
            stormBeamWave(ray, sceneDistance, i, light, chipAlpha, chipColor, distortion);
            continue;
        }
        if (charging > 18.5) {
            shamanWardWave(ray, sceneDistance, scenePoint, depth, i, light, distortion);
            continue;
        }
        if (charging > 17.5) {
            shamanTotemWave(ray, sceneDistance, scenePoint, depth, i, light, chipAlpha, chipColor, distortion);
            continue;
        }
        if (charging > 16.5) {
            hunterHighlight(ray, sceneDistance, i, light);
            continue;
        }
        if (charging > 15.5) {
            demolitionBlast(ray, sceneDistance, i, light, distortion);
            continue;
        }
        if (charging > 14.5) {
            float victimEnv = QuakeMaterials[i * 32].x;
            if (victimEnv > chronoStunStrength) {
                chronoStunStrength = victimEnv;
                chronoStunAge = age;
            }
            chronosphereWave(ray, sceneDistance, scenePoint, depth, i, light, darkness, chipAlpha, chipColor, distortion, skyOverride, skyOverrideAlpha);
            continue;
        }
        if (charging > 13.5) {
            broadswordSweep(ray, sceneDistance, i, light, chipAlpha, chipColor, distortion);
            continue;
        }
        if (charging > 12.5) {
            weaponStreak(ray, sceneDistance, i, light, chipAlpha, chipColor, distortion);
            continue;
        }
        if (charging > 11.5) {
            if (WaveStyles[i].w > 0.0) {
                ghoulStrength = max(ghoulStrength, WaveStyles[i].w);
                ghoulAge = age;
                ghoulScale = QuakeMaterials[i * 32 + 9].w;
                ghoulBurst = max(ghoulBurst, QuakeMaterials[i * 32 + 10].x);
                ghoulHeartbeat = max(ghoulHeartbeat, QuakeMaterials[i * 32 + 10].y);
            }
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
            maskBurst = QuakeMaterials[i * 32].x;
            maskParams = WaveOrigins[i].xyz;
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
            maskBurst = QuakeMaterials[i * 32].x;
            maskParams = WaveOrigins[i].xyz;
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
        gravitationalDissonanceWave(ray, sceneDistance, scenePoint, depth, i, light, tint, distortion);
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
        float wave = sin(radius * 12.0 - ImpactConfig.y * 4.0) * 0.22 + 0.78;
        pressure = radial * ImpactConfig.x * (0.018 + 0.010 * wave);
        split += radial * ImpactConfig.y * edge * 0.005;
    }
    float aspectRatio = float(textureSize(InSampler, 0).x) / float(textureSize(InSampler, 0).y);
    vec2 maskUv = (texCoord - 0.5) * vec2(aspectRatio, 1.0);
    float maskField = 0.0;
    computeMaskWarpAndField(maskStrength, maskScale, maskAge, maskParams, maskUv, pressure, maskField);
    computeChronoStunWarp(chronoStunStrength, chronoStunAge, maskUv, pressure, split);

    if (maskBurst > 0.001) {
        vec2 radialBurst = texCoord - 0.5;
        split += radialBurst * (0.35 + length(radialBurst) * 1.4) * maskBurst * 0.05 * min(maskScale, 1.5);
    }
    if (ghoulBurst > 0.001) {
        vec2 radialGhoul = texCoord - 0.5;
        split += radialGhoul * (0.28 + length(radialGhoul) * 1.15) * ghoulBurst * 0.032 * min(ghoulScale, 1.5);
    }
    if (critAlpha > 0.0) {
        float flash = exp(-critAge * 30.0) * min(critAlpha * 2.0, 1.0);
        vec2 push = (texCoord - 0.5) * vec2(aspectRatio, 1.0) - critUv;
        pressure += push * flash * 0.016;
        split += push * flash * 0.005;
    }
    vec2 lower = pixel * 0.5;
    vec2 upper = vec2(1.0) - lower;
    vec2 uv = texCoord + pressure + split * 0.55;
    vec3 refracted = original.rgb;
    if (dot(split, split) < 1e-12 && dot(pressure, pressure) > 1e-12) {
        refracted = texture(InSampler, clamp(uv, lower, upper)).rgb;
    } else if (dot(split, split) + dot(pressure, pressure) > 1e-12) {
        refracted = vec3(
            texture(InSampler, clamp(uv + split, lower, upper)).r,
            texture(InSampler, clamp(uv, lower, upper)).g,
            texture(InSampler, clamp(uv - split, lower, upper)).b
        );
    }
    vec3 worldColor = mix(refracted, CYAN, min(tint, 0.68)) * (1.0 - min(darkness, 0.9));
    vec3 color = mix(worldColor, skyOverride, clamp(skyOverrideAlpha, 0.0, 1.0)) + min(light, vec3(0.85));
    color = mix(color, chipColor / max(chipAlpha, 0.00001), min(chipAlpha, 1.0));

    applyMaskAndCrystalOverlay(maskStrength, maskScale, maskAge, maskCrystal, maskField, maskParams, maskUv, color);
    applyBulwarkScreenOverlay(bulwarkStrength, bulwarkScale, bulwarkAge, bulwarkPulse, bulwarkAbsorb, aspectRatio, color);
    applyBloodlustScreenOverlay(ghoulStrength, ghoulScale, ghoulAge, ghoulHeartbeat, ghoulBurst, aspectRatio, color);
    applyChronoStunOverlay(chronoStunStrength, chronoStunAge, maskUv, color);

    fragColor = vec4(color, original.a);
}
