// Weapons: Axe of Cumshot / Jungle Spear streak and Chaos Broadsword sweep.

void weaponStreak(vec3 ray, float sceneDistance, int wave,
                  inout vec3 light, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    float alpha = WaveStyles[wave].x;
    if (alpha <= 0.001) return;
    vec3 start = WaveOrigins[wave].xyz;
    vec4 pack = QuakeMaterials[wave * 32];
    vec3 end = pack.xyz;
    float width = max(0.015, pack.w);
    vec3 segment = end - start;
    float lengthSquared = dot(segment, segment);
    if (lengthSquared < 0.0001) return;
    float halfLength = sqrt(lengthSquared) * 0.5;
    if (!intersectsBound(ray, sceneDistance, start + segment * 0.5, halfLength + width * 5.0)) return;

    float progress = clamp(WaveStyles[wave].w, 0.0, 1.0);
    float b = dot(ray, segment);
    float d = dot(ray, start);
    float e = dot(segment, start);
    float along = clamp((b * d - e) / max(0.00001, lengthSquared - b * b), 0.0, 1.0);
    vec3 point = start + segment * along;
    float hit = dot(point, ray);
    if (hit <= 0.0 || hit > sceneDistance) return;

    vec3 separation = ray * hit - point;
    float distanceSquared = dot(separation, separation) / (width * width);
    float streak = exp(-distanceSquared);
    float reveal = smoothstep(along - 0.04, along + 0.04, progress);
    float tail = smoothstep(progress - 0.55, progress - 0.05, along);
    float fade = 1.0 - smoothstep(0.72, 1.0, progress);
    float intensity = streak * reveal * tail * fade * alpha;

    vec3 headPoint = start + segment * progress;
    vec3 headDelta = ray * max(dot(headPoint, ray), 0.0) - headPoint;
    float headGlow = exp(-dot(headDelta, headDelta) / max(0.0001, width * width * 9.0)) * fade * alpha;

    vec3 color = QuakeMaterials[wave * 32 + 1].rgb;
    light += color * (intensity * 0.95 + headGlow * 0.55);
    float coverage = clamp(intensity * 1.4 + headGlow * 0.9, 0.0, 1.0);
    chipColor += color * coverage;
    chipAlpha += coverage;
    vec2 radial = texCoord - screenPosition(point);
    distortion += radial / max(length(radial), 0.001) * intensity * 0.14;
}

void broadswordSweep(vec3 ray, float sceneDistance, int wave,
                     inout vec3 light, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    float alpha = WaveStyles[wave].x;
    if (alpha <= 0.001) return;
    vec3 origin = WaveOrigins[wave].xyz;
    float radius = WaveOrigins[wave].w;
    float age = WaveStyles[wave].y;
    float progress = clamp(WaveStyles[wave].w, 0.0, 1.0);
    if (!intersectsBound(ray, sceneDistance, origin, radius + 0.6)) return;

    vec3 color = QuakeMaterials[wave * 32 + 1].rgb;
    color = length(color) < 0.01 ? vec3(1.0, 0.42, 0.10) : color;
    goldenFloorRing(ray, sceneDistance, origin, radius, 0.12, alpha * (1.0 - progress) * 0.8, color, light);

    float along = max(dot(ray, origin), 0.0);
    vec3 point = ray * along;
    vec3 relative = point - origin;
    float radiusDistance = length(relative);
    float band = exp(-pow((radiusDistance - radius) / (0.10 + 0.07 * progress), 2.0));
    float spin = 0.55 + 0.45 * sin(atan(relative.z, relative.x) * 3.0 + age * 24.0);
    float intensity = band * alpha * spin * (1.0 - smoothstep(0.62, 1.0, progress));
    light += color * intensity;
    float coverage = clamp(intensity * 1.2, 0.0, 1.0);
    chipColor += color * coverage;
    chipAlpha += coverage;
    vec2 radial = texCoord - screenPosition(origin);
    distortion += radial / max(length(radial), 0.001) * intensity * 0.18;
}
