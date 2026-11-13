// Class: Demolitionist ("Підривник") — Thermal blast shockwave & concussion refraction

void demolitionBlast(vec3 ray, float sceneDistance, int i, inout vec3 light, inout vec2 distortion) {
    vec3 centre = WaveOrigins[i].xyz;
    float radius = WaveOrigins[i].w;
    float alpha = WaveStyles[i].x;
    float age = WaveStyles[i].y;
    float mineMode = WaveStyles[i].w;
    if (alpha <= 0.001 || !intersectsBound(ray, sceneDistance, centre, radius + 1.4)) return;

    // Warm expanding floor blast rings (matches real knockback/explosion radius)
    vec3 ringCore = mix(vec3(1.0, 0.72, 0.24), vec3(1.0, 0.44, 0.14), mineMode);
    vec3 ringEdge = mix(vec3(1.0, 0.40, 0.08), vec3(0.95, 0.20, 0.06), mineMode);
    goldenFloorRing(ray, sceneDistance, centre + vec3(0.0, 0.05, 0.0), radius, 0.14, alpha * 0.72, ringEdge, light);
    goldenFloorRing(ray, sceneDistance, centre + vec3(0.0, 0.05, 0.0), radius * 0.68, 0.10, alpha * 0.42 * exp(-age * 4.5), ringCore, light);

    // Core thermal flash glow + subtle spherical heat-wave refraction
    float along = dot(ray, centre);
    if (along > 0.0 && along < sceneDistance + 0.5) {
        float dist = length(ray * along - centre);
        float flashRadius = 0.42 + radius * 0.48;
        float flash = exp(-(dist * dist) / max(flashRadius * flashRadius, 0.02)) * exp(-age * 9.5) * alpha;
        light += mix(vec3(1.0, 0.78, 0.36), vec3(1.0, 0.46, 0.16), mineMode) * flash * 0.85;

        float shellBand = exp(-pow((dist - radius * 0.85) / 0.34, 2.0));
        float pressureEnv = alpha * (1.0 - smoothstep(0.06, 0.42, age));
        vec2 push = texCoord - screenPosition(centre);
        float pLen = length(push);
        if (pLen > 0.0005) {
            distortion += (push / pLen) * (shellBand * 0.42 + flash * 0.30) * pressureEnv;
        }
    }
}
