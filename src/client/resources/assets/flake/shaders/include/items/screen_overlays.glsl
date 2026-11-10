// First-person screen overlays and refraction warps for Matadora, Crystal Claw, and impacts.

void computeMaskWarpAndField(float maskStrength, float maskScale, float maskAge, vec3 maskParams,
                             vec2 maskUv, inout vec2 pressure, out float maskField) {
    maskField = 0.0;
    if (maskStrength <= 0.0) return;
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

void applyMaskAndCrystalOverlay(float maskStrength, float maskScale, float maskAge, float maskCrystal,
                                float maskField, vec3 maskParams, vec2 maskUv, inout vec3 color) {
    if (maskStrength <= 0.0) return;
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
    color = mix(color, vec3(luma), min(desat * s * dens, 0.5));
    color = mix(color, color * tintMul + tintAdd, min((0.32 + 0.12 * maskField) * s * dens, 0.7));
    color += edgeTint * vignette * (0.10 + 0.08 * maskField) * s * dens;
    color *= 1.0 - min(vignette * mix(0.42, 0.34, maskCrystal) * s * dens + 0.03 * s * dens, 0.65);
    color += grain * mix(0.11, 0.075, maskCrystal) * s * noiseAmt;
}
