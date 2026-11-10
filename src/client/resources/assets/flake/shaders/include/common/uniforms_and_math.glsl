// Shared uniforms, colour constants, projection math, noise and floor-ring helpers.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

layout(std140) uniform ShockwaveConfig {
    mat4 ViewProjection;
    mat4 InverseViewProjection;
    vec4 DepthConfig;
    vec4 WaveOrigins[4]; // Camera-relative xyz, radius in blocks.
    vec4 WaveStyles[4];  // Opacity, age in seconds, charging flag, charge progress.
    vec4 ImpactConfig; // Signed pressure, aberration envelope, screen-space centre xy.
    vec4 MoteOrigins[128]; // 32 per wave; negative radius denotes a ground fragment.
    vec4 MoteTails[128];
    vec4 QuakeMaterials[128]; // Sampled block colour, rotation angle.
    vec4 QuakeStarts[256]; // Cached terrain channel start, width.
    vec4 QuakeEnds[256]; // Progressively revealed endpoint, intensity.
    vec4 QuakeBounds[32]; // Eight small conservative spatial groups per cast.
};

const vec3 CYAN = vec3(0.82, 0.91, 1.0);
const vec3 ICE = vec3(1.0, 0.985, 0.96);
const vec3 ELECTRIC_BLUE = vec3(0.18, 0.56, 1.0);
const vec3 ELECTRIC_CYAN = vec3(0.42, 0.88, 1.0);
const vec3 ELECTRIC_CORE = vec3(0.88, 0.97, 1.0);
const vec3 VOID_VIOLET = vec3(0.42, 0.10, 0.80);
const vec3 VOID_BRIGHT = vec3(0.88, 0.58, 1.0);
const vec3 CRYSTAL_DEEP = vec3(0.16, 0.52, 0.86);
const vec3 CRYSTAL_BRIGHT = vec3(0.72, 0.95, 1.0);
const vec3 CRYSTAL_TINT = vec3(0.46, 0.82, 1.0);

// Conservative ray/sphere rejection before any expensive surface/particle work.
bool intersectsBound(vec3 ray, float sceneDistance, vec3 centre, float radius) {
    float along = dot(ray, centre);
    if (along + radius < 0.0 || along - radius > sceneDistance) return false;
    return dot(centre, centre) - along * along <= radius * radius;
}

vec3 unproject(float depth) {
    vec4 p = InverseViewProjection * vec4(texCoord * 2.0 - 1.0, depth * DepthConfig.x + DepthConfig.y, 1.0);
    return p.xyz / p.w;
}

vec2 screenPosition(vec3 point) {
    vec4 clip = ViewProjection * vec4(point, 1.0);
    return clip.xy / max(abs(clip.w), 0.0001) * 0.5 + 0.5;
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float softNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x), f.y);
}

void goldenFloorRing(vec3 ray, float sceneDistance, vec3 centre, float radius, float width, float alpha, vec3 col, inout vec3 light) {
    if (alpha <= 0.001 || abs(ray.y) < 0.0001) return;
    float hit = centre.y / ray.y;
    if (hit <= 0.0 || hit > sceneDistance + 0.02) return;
    vec2 rel = (ray * hit - centre).xz;
    float r = length(rel);
    float diff = (r - radius) / width;
    if (abs(diff) > 3.2) return;
    float band = exp(-diff * diff);
    light += col * band * alpha;
}
