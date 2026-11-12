// Weapon: Void Paladin ("Порожній Паладин") — 4th-hit critical slash
// Живий піксель-арт слеш: пружинний скейл з перельотом, squash & stretch,
// 3D-хитання площини, тряска, дрижання пікселів по кадрах, ехо-слід,
// краплі з відскоком від землі, спалах, кільце і акуратний глов.

const float PX = 0.04;                        // розмір "пікселя" текстури
const float SA = 0.55;                        // кут слешу (зліва направо вгору)
const vec3 P0 = vec3(0.28, 0.0, 0.05);        // темний контур
const vec3 P1 = vec3(0.72, 0.04, 0.10);       // основний червоний
const vec3 P2 = vec3(0.98, 0.22, 0.20);       // яскравий
const vec3 P3 = vec3(1.0, 0.86, 0.80);        // гаряче ядро
const vec3 GLOW = vec3(0.86, 0.14, 0.16);

float h11(float n) {
    n = fract(n * 0.1031);
    n *= n + 33.33;
    n *= n + n;
    return fract(n);
}

vec2 rot2(vec2 p, float a) {
    float c = cos(a), s = sin(a);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

// пружина: 0 -> перельот ~1.27 -> 1
float spring(float x) {
    x = clamp(x, 0.0, 1.0);
    return 1.0 - exp(-6.0 * x) * cos(14.0 * x);
}

// скейл + squash/stretch вздовж леза
vec2 slashSpace(vec2 p, float sc, float bn) {
    vec2 q = rot2(p / sc, -SA);
    q *= vec2(1.0 / (1.0 + 0.3 * bn), 1.0 + 0.6 * bn);
    return rot2(q, SA);
}

// півмісяць між двома дугами зі спільною хордою; u = позиція вздовж леза 0..1
void slashEval(vec2 p, float L, float s1, float s2, float t0, float t,
               out float d, out float u, out float head, out float tail, out float sw) {
    vec2 q = rot2(p, -SA);
    q.y += s1 * 0.5;
    float R1 = (L * L + s1 * s1) / (2.0 * s1);
    float R2 = (L * L + s2 * s2) / (2.0 * s2);
    d = max(length(q - vec2(0.0, s1 - R1)) - R1, R2 - length(q - vec2(0.0, s2 - R2)));
    u = q.x / (2.0 * L) + 0.5;
    sw = clamp((t - t0) / 0.1, 0.0, 1.0);
    head = 1.0 - pow(1.0 - sw, 3.0);           // різкий ривок з уповільненням
    float tw = clamp((t - t0 - 0.16) / 0.3, 0.0, 1.0);
    tail = tw * tw;                            // слід стирається з початку
}

bool slashPix(vec2 p, float L, float s1, float s2, float t0, float t, float jit, out vec3 col) {
    float d, u, head, tail, sw;
    slashEval(p, L, s1, s2, t0, t, d, u, head, tail, sw);
    d += jit;                                  // дрижання контуру по кадрах
    col = P0;
    if (d > 0.0 || u > head || u < tail) return false;
    float f = clamp(-d / ((s1 - s2) * 0.5), 0.0, 1.0);
    f += 0.45 * (1.0 - smoothstep(0.0, 0.18, head - u)) * (1.0 - sw);   // яскрава голова
    col = f < 0.22 ? P0 : (f < 0.5 ? P1 : (f < 0.8 ? P2 : P3));
    return true;
}

float slashGlow(vec2 p, float L, float s1, float s2, float t0, float t) {
    float d, u, head, tail, sw;
    slashEval(p, L, s1, s2, t0, t, d, u, head, tail, sw);
    float m = (1.0 - smoothstep(head - 0.03, head + 0.05, u)) * smoothstep(tail - 0.05, tail + 0.03, u);
    m *= step(-0.05, u) * step(u, 1.05) * step(0.001, sw);
    float x = max(d, 0.0) * 9.0;
    return m / (1.0 + x * x);
}

void critBurst(vec3 ray, float sceneDistance, int wave,
               inout vec3 light, inout float darkness, inout float chipAlpha, inout vec3 chipColor, inout vec2 distortion) {
    vec4 body = QuakeBounds[wave * 8];
    if (body.w <= 0.0) return;

    float alpha = WaveStyles[wave].x;
    float t = WaveStyles[wave].y;
    if (alpha <= 0.0 || t < 0.0 || t > 0.75) return;

    vec3 pivot = body.xyz;
    if (!intersectsBound(ray, sceneDistance, pivot, 3.2)) return;

    // "життя": відскок, пружинний скейл, хитання площини
    float bn = exp(-t * 12.0) * cos(t * 40.0);
    float sc = mix(0.5, 1.0, spring(t / 0.35)) * (1.0 - 0.3 * smoothstep(0.5, 0.75, t));
    float tilt = 0.9 * exp(-t * 8.0) * cos(t * 26.0);

    // площина-білборд до камери, з 3D-нахилом
    vec3 cam = vec3(0.0);                      // камера = початок координат
    vec3 toCam = cam - pivot;
    float distToCam = length(toCam);
    if (distToCam < 0.05) return;
    vec3 n = toCam / distToCam;
    vec3 rgt = normalize(cross(vec3(0.0, 1.0, 0.0001), n));
    n = normalize(n + rgt * tilt);
    rgt = normalize(cross(vec3(0.0, 1.0, 0.0001), n));
    vec3 upv = cross(n, rgt);
    float dn = dot(ray, n);
    if (dn > -0.0001) return;
    float tp = dot(pivot - cam, n) / dn;
    if (tp <= 0.0 || tp > sceneDistance) return;
    vec3 wp = cam + ray * tp - pivot;
    vec2 uv = vec2(dot(wp, rgt), dot(wp, upv));
    uv += vec2(sin(t * 95.0), cos(t * 83.0)) * 0.035 * exp(-t * 14.0);   // тряска удару
    if (dot(uv, uv) > 9.0) return;

    vec2 cell = floor(uv / PX);
    vec2 pq = (cell + 0.5) * PX;               // піксельна сітка
    vec2 p2 = slashSpace(pq, sc, bn);          // простір леза
    float fr = floor((t / 0.6) * 14.0);        // 14 "кадрів" дрижання
    float jit = (h11(dot(cell, vec2(12.9898, 78.233)) + fr * 1.7) - 0.5) * 0.045;

    float L = 1.5, S1 = 0.38 + 0.1 * max(bn, 0.0), S2 = 0.04;

    // глов (м'який, поза піксельною сіткою) + пульс
    float life = 1.0 - smoothstep(0.3, 0.7, t);
    float g = slashGlow(slashSpace(uv, sc, bn), L, S1, S2, 0.0, t);
    float ft = (t - 0.02) / 0.12;
    float flash = (ft > 0.0 && ft < 1.0) ? (1.0 - ft) / (1.0 + dot(uv, uv) * 18.0) : 0.0;
    // Deadly Bleed reuses the exact silhouette through an orange palette.
    bool orangePalette = QuakeMaterials[wave * 32 + 1].y > 0.5;
    vec3 glowColor = orangePalette ? vec3(1.0, 0.42, 0.06) : GLOW;
    light += glowColor * (g * 0.3 * life * (1.0 + 0.5 * bn) + flash * 0.6) * alpha;

    vec3 col = vec3(0.0);
    float a = 0.0;

    // ударне кільце з розривами
    float rt = clamp((t - 0.02) / 0.3, 0.0, 1.0);
    if (rt > 0.0 && rt < 1.0) {
        float rr = 0.25 + 1.5 * (1.0 - pow(1.0 - rt, 3.0));
        float rd = abs(length(pq) - rr);
        float gap = h11(floor(atan(pq.y, pq.x) * 5.0) + 7.0);
        if (rd < PX * 1.1 * (1.0 - rt) + 0.001 && gap > 0.3) { col = rt < 0.4 ? P2 : P1; a = 1.0; }
    }

    // ехо-слід (запізніла копія) + основний слеш
    vec3 sc3;
    if (slashPix(p2, L, S1, S2, 0.0, t - 0.05, jit, sc3)) { col = P1; a = 1.0; }
    if (slashPix(p2, L, S1, S2, 0.0, t, jit, sc3)) { col = sc3; a = 1.0; }

    // піксельні краплі зі слідом і відскоком від землі
    vec2 dir1 = vec2(cos(SA), sin(SA));
    vec2 nrm = vec2(-dir1.y, dir1.x);
    const float FLOOR = -1.35;
    for (int i = 0; i < 16; i++) {
        float fi = float(i);
        float k = h11(fi * 1.7 + 1.0), k2 = h11(fi * 3.1 + 5.0), k3 = h11(fi * 5.3 + 9.0);
        float da = (t - 0.03 - 0.06 * k) / 0.55;
        if (da < 0.0 || da > 1.0) continue;
        float ae = 1.0 - (1.0 - da) * (1.0 - da);
        vec2 v = nrm * ((k2 > 0.5 ? 1.0 : -1.0) * (0.4 + k3)) + dir1 * (k2 - 0.5) * 1.2;
        vec2 dp = dir1 * ((k - 0.5) * 2.6) * sc + v * 1.3 * ae - vec2(0.0, 2.6 * da * da);
        if (dp.y < FLOOR) dp.y = FLOOR + (FLOOR - dp.y) * 0.45;           // відскок
        vec2 sp = (floor(dp / PX) + 0.5) * PX;
        float big = (k3 > 0.7 && da < 0.6) ? 1.5 : 0.5;
        vec2 df = abs(pq - sp);
        if (max(df.x, df.y) < big * PX * 1.01) {
            col = (big > 1.0 && max(df.x, df.y) > 0.6 * PX) ? P1 : P2;
            a = 1.0;
        }
        vec2 tdir = normalize(v);
        for (int j = 1; j < 3; j++) {
            vec2 sp2 = (floor((dp - tdir * PX * 1.6 * float(j)) / PX) + 0.5) * PX;
            vec2 d2 = abs(pq - sp2);
            if (max(d2.x, d2.y) < 0.51 * PX && da < 0.8) { col = j == 1 ? P1 : P0; a = 1.0; }
        }
    }

    // спалах-зірка в точці удару (довгі промені вздовж леза)
    if (ft > 0.0 && ft < 1.0) {
        float r = length(pq);
        float ang = atan(pq.y, pq.x);
        float sa = pow(abs(cos(ang - SA)), 16.0);
        float sb = pow(abs(sin(ang - SA)), 16.0);
        float rad = (0.09 + 0.95 * max(sa, 0.35 * sb)) * (1.0 - ft) * (0.7 + 0.5 * spring(ft * 0.6));
        if (r < rad) { col = r < rad * 0.5 ? P3 : P2; a = 1.0; }
    }

    if (orangePalette && a > 0.0) {
        float luminance = clamp(dot(col, vec3(0.299, 0.587, 0.114)), 0.0, 1.0);
        col = mix(vec3(0.32, 0.09, 0.01), vec3(1.0, 0.88, 0.45), luminance);
    }
    a *= alpha;
    chipColor += col * a;
    chipAlpha += a;
}
