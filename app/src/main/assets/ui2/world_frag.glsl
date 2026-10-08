#version 300 es
precision highp float;
precision highp sampler2D;

in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uScenery;
uniform float uSceneryHorizon;
uniform int uLandscapeScenery;
uniform vec2 uResolution;
uniform float uTime;
uniform vec4 uAudio; // shared live RMS, bass, treble, event envelope
uniform float uImpulseAge;
uniform float uRoute;
uniform float uOrbit;
uniform float uMotion;
uniform int uLensCount;
uniform vec4 uLenses[5]; // normalized top-origin center, short-edge radius, selected

const float PI = 3.14159265359;
const float FOCAL = 0.66;
vec3 camera = vec3(0.0);
vec3 forwardDirection = vec3(0.0, 0.0, -1.0);
vec3 rightDirection = vec3(1.0, 0.0, 0.0);
vec3 upDirection = vec3(0.0, 1.0, 0.0);
vec3 hero = vec3(0.0);
mat2 heroOrientation = mat2(1.0);
float heroRadius = 0.72;
float time = 0.0;
float aspect = 1.0;

float hash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float mineralNoise(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash(cell), hash(cell + vec3(1, 0, 0)), f.x),
            mix(hash(cell + vec3(0, 1, 0)), hash(cell + vec3(1, 1, 0)), f.x), f.y),
        mix(mix(hash(cell + vec3(0, 0, 1)), hash(cell + vec3(1, 0, 1)), f.x),
            mix(hash(cell + vec3(0, 1, 1)), hash(cell + vec3(1, 1, 1)), f.x), f.y), f.z);
}

vec3 screenRay(vec2 topOrigin) {
    vec2 q = vec2(topOrigin.x * 2.0 - 1.0, 1.0 - topOrigin.y * 2.0);
    q.x *= aspect;
    return normalize(forwardDirection + FOCAL * (q.x * rightDirection + q.y * upDirection));
}

vec3 source(vec2 topOrigin) {
    // Android bitmaps upload their first row at texture y=0; keep that contract in captures.
    return texture(uScenery, clamp(topOrigin, vec2(0.001), vec2(0.999))).rgb;
}

vec2 projectToScenery(vec3 direction) {
    float depth = max(dot(direction, forwardDirection), 0.01);
    vec2 q = vec2(dot(direction, rightDirection), dot(direction, upDirection)) / (depth * FOCAL);
    return vec2(0.5 + q.x / (2.0 * aspect), 0.5 - q.y * 0.5);
}

float horizonOnScreen() {
    return 0.5 + forwardDirection.y / (2.0 * FOCAL * upDirection.y);
}

vec3 environment(vec3 direction) {
    float azimuth = atan(direction.x, -direction.z);
    float elevation = atan(direction.y, length(direction.xz));
    vec2 uv = vec2(0.5 + azimuth * 0.32, uSceneryHorizon - elevation * 0.85);
    // The separate landscape matte already includes photographic perspective. Calibrate its
    // visible far layer to our camera, rather than applying a second spherical image stretch.
    if (uLandscapeScenery != 0 && dot(direction, forwardDirection) > 0.05) {
        vec2 projected = projectToScenery(direction);
        uv = vec2(projected.x, projected.y * uSceneryHorizon / horizonOnScreen());
    }
    // The reflected environment is the sky and far shore, never a second photograph of water.
    vec3 color = source(vec2(uv.x, clamp(uv.y, 0.0, uSceneryHorizon)));
    float sunlight = pow(max(dot(direction, normalize(vec3(0.52, 0.64, -0.38))), 0.0), 90.0);
    return color + vec3(0.27, 0.23, 0.14) * sunlight;
}

vec3 orientedLocal(vec3 p) {
    p.xz = heroOrientation * p.xz;
    return p / heroRadius;
}

float crystalDistance(vec3 point) {
    vec3 p = orientedLocal(point);
    p.y += p.x * 0.12;
    // The long, leaning shell has asymmetric fractured faces and a broad, low-frequency relief.
    // These bounded analytic fields keep the same 48-step march budget; grain is shaded later.
    p.x += p.y * 0.045;
    float shape = length(p * vec3(1.17, 0.88, 1.10)) - 1.0;
    shape += sin(p.x * 5.0 + p.y * 4.0) * sin(p.z * 6.0 - p.y * 3.0) * 0.014;
    shape = max(shape, dot(p, normalize(vec3(0.73, 0.58, 0.37))) - 0.88);
    shape = max(shape, dot(p, normalize(vec3(-0.64, 0.54, 0.55))) - 0.93);
    shape = max(shape, dot(p, normalize(vec3(0.15, -0.88, 0.48))) - 0.84);
    shape = max(shape, dot(p, normalize(vec3(0.72, -0.36, -0.60))) - 0.86);
    shape = max(shape, dot(p, normalize(vec3(-0.80, -0.28, -0.51))) - 0.82);
    shape = max(shape, dot(p, normalize(vec3(0.11, 0.95, -0.28))) - 1.00);
    // A tall scooped chamber and two chipped lip openings reveal independent crystal volumes.
    vec3 chamberPoint = (p - vec3(-0.04, 0.07, 0.69)) * vec3(1.27, 0.87, 1.0);
    float chamber = length(chamberPoint) - 0.68;
    chamber += sin(p.y * 11.0 + p.x * 4.0) * sin(p.z * 7.0) * 0.018;
    chamber = min(chamber, length(p - vec3(-0.42, 0.49, 0.65)) - 0.15);
    chamber = min(chamber, length(p - vec3(0.35, -0.35, 0.69)) - 0.12);
    shape = max(shape, -chamber);
    return shape * heroRadius * 0.78;
}

float quartzShard(vec3 p, float halfHeight, float radius, float lean) {
    p.x -= p.y * lean;
    // Six prism faces and oblique crown planes give actual quartz facets, including its tip.
    float side = max(abs(p.x) * 0.8660254 + abs(p.z) * 0.5, abs(p.z)) - radius * 0.8660254;
    float crown = max(abs(p.x) * 0.48 + p.y * 0.87, abs(p.z) * 0.48 + p.y * 0.87) - halfHeight * 0.77;
    float foot = -p.y - halfHeight * 0.85;
    return max(side, max(crown, foot)) * 0.82;
}

float coreDistance(vec3 point) {
    vec3 p = orientedLocal(point);
    // Three fixed analytic shards share one material; no meshes, extra textures or dynamic loops.
    float mainShard = quartzShard(p - vec3(-0.025, 0.06, 0.36), 0.64, 0.235, -0.07);
    float leftShard = quartzShard(p - vec3(-0.20, -0.12, 0.32), 0.46, 0.17, -0.28);
    float rightShard = quartzShard(p - vec3(0.19, -0.22, 0.34), 0.37, 0.155, 0.25);
    return min(mainShard, min(leftShard, rightShard)) * heroRadius;
}

vec2 sceneDistance(vec3 point) {
    float crystal = crystalDistance(point - hero);
    float core = coreDistance(point - hero);
    vec3 basePoint = point - vec3(hero.x, 0.14, hero.z);
    vec3 stretch = vec3(1.0, 0.33, 0.87);
    float base = (length(basePoint / stretch) - heroRadius * 0.87) * 0.33;
    vec2 mineral = crystal < base ? vec2(crystal, 2.0) : vec2(base, 1.0);
    return core < mineral.x ? vec2(core, 3.0) : mineral;
}

float sphereHit(vec3 origin, vec3 direction, vec3 center, float radius) {
    vec3 local = origin - center;
    float b = dot(local, direction);
    float c = dot(local, local) - radius * radius;
    float discriminant = b * b - c;
    if (discriminant < 0.0) return -1.0;
    float nearHit = -b - sqrt(discriminant);
    return nearHit > 0.0 ? nearHit : -b + sqrt(discriminant);
}

vec2 march(vec3 origin, vec3 direction, float maximum) {
    float distance = 0.0;
    float material = -1.0;
    for (int step = 0; step < 48; ++step) {
        vec2 field = sceneDistance(origin + direction * distance);
        if (field.x < 0.0017) { material = field.y; break; }
        distance += max(field.x * 0.80, 0.001);
        if (distance > maximum) break;
    }
    return vec2(distance, material);
}

vec3 sceneNormal(vec3 point) {
    vec2 e = vec2(0.0025, 0.0);
    return normalize(vec3(
        sceneDistance(point + e.xyy).x - sceneDistance(point - e.xyy).x,
        sceneDistance(point + e.yxy).x - sceneDistance(point - e.yxy).x,
        sceneDistance(point + e.yyx).x - sceneDistance(point - e.yyx).x));
}

vec3 sphereExitRay(vec3 point, vec3 normal, vec3 direction, vec3 center, float radius,
                   float refractiveIndex, out vec3 exitPoint, out float thickness) {
    vec3 refracted = refract(direction, normal, 1.0 / refractiveIndex);
    // Both sphere boundaries use the same index; the outgoing ray starts beyond the actual exit.
    vec3 entry = point + refracted * 0.003;
    vec3 local = entry - center;
    float b = dot(local, refracted);
    thickness = max(0.0, -b + sqrt(max(0.0, b * b - dot(local, local) + radius * radius)));
    exitPoint = entry + refracted * thickness;
    vec3 exitNormal = normalize(exitPoint - center);
    vec3 exiting = refract(refracted, -exitNormal, refractiveIndex);
    if (dot(exiting, exiting) < 0.01) exiting = reflect(refracted, exitNormal);
    return normalize(exiting);
}

vec3 quartzColor(vec3 point, vec3 normal, vec3 direction) {
    float facing = clamp(dot(-direction, normal), 0.0, 1.0);
    float fresnel = 0.035 + 0.965 * pow(1.0 - facing, 5.0);
    vec3 local = orientedLocal(point - hero);
    vec3 transmitted = environment(refract(direction, normal, 1.0 / 1.46));
    vec3 reflection = environment(reflect(direction, normal));
    vec3 lightDirection = normalize(vec3(-0.48, 0.72, 0.50));
    float sparkle = pow(max(dot(reflect(-lightDirection, normal), -direction), 0.0), 100.0);
    float innerLight = exp(-dot(local.xy * vec2(2.0, 0.8), local.xy * vec2(2.0, 0.8)));
    float faceLight = 0.68 + 0.32 * max(dot(normal, lightDirection), 0.0);
    vec3 color = mix(transmitted * vec3(0.29, 0.69, 0.85), reflection, fresnel * 0.80);
    color += vec3(0.015, 0.31, 0.49) * innerLight * faceLight * (0.50 + uAudio.x * 0.27 + uAudio.w * 0.20);
    color += vec3(0.82, 0.98, 1.0) * sparkle * 0.58;
    // Fine internal planes have low contrast; the visible silhouette comes from real facets.
    float inclusion = exp(-abs(local.y * 0.38 + local.x * 0.81 - local.z * 0.26 - 0.12) * 80.0);
    color += vec3(0.08, 0.42, 0.56) * inclusion * 0.10;
    return color;
}

vec3 shadeObject(vec3 origin, vec3 direction, vec2 hit) {
    vec3 point = origin + direction * hit.x;
    vec3 normal = sceneNormal(point);
    vec3 lightDirection = normalize(vec3(-0.48, 0.72, 0.50));
    if (hit.y < 2.5) {
        vec3 local = hit.y > 1.5 ? orientedLocal(point - hero) :
                     (point - vec3(hero.x, 0.14, hero.z)) / heroRadius;
        float diffuse = max(dot(normal, lightDirection), 0.0);
        float grain = mineralNoise(local * 25.0) * 0.72 + mineralNoise(local * 61.0) * 0.28;
        float broadMineral = mineralNoise(local * 3.8 + vec3(2.3, 0.0, 1.7));
        float wetShine = pow(max(dot(reflect(-lightDirection, normal), -direction), 0.0), 52.0);
        vec3 rock = mix(vec3(0.22, 0.28, 0.29), vec3(0.47, 0.50, 0.46), broadMineral);
        if (hit.y < 1.5) rock *= vec3(0.75, 0.79, 0.73);
        float moss = smoothstep(0.67, 0.86, broadMineral) * smoothstep(0.0, 0.45, normal.y + 0.16);
        rock = mix(rock, vec3(0.17, 0.24, 0.18), moss * 0.64);
        vec3 color = rock * (0.63 + diffuse * 0.55) * (0.86 + grain * 0.22);
        float grazing = pow(1.0 - max(dot(-direction, normal), 0.0), 4.0);
        color += environment(reflect(direction, normal)) * (wetShine * 0.25 + grazing * 0.055);
        if (hit.y > 1.5) {
            color *= 0.68 + smoothstep(-0.10, 0.70, local.z) * 0.32;
            float fissure = min(abs(local.x + local.y * 0.32 + sin(local.y * 6.0) * 0.045),
                                abs(local.y - local.z * 0.28 + sin(local.z * 7.0) * 0.065));
            float seam = exp(-fissure * 115.0) * smoothstep(-0.1, 0.4, local.z);
            color += vec3(0.025, 0.50, 0.66) * seam * (0.24 + uAudio.x * 0.24 + uAudio.w * 0.22);
            color += vec3(0.00, 0.10, 0.16) * exp(-fissure * 23.0) * 0.10;
        }
        return color;
    }
    return quartzColor(point, normal, direction);
}

vec3 waterNormal(vec3 point) {
    float amplitude = 0.005 + uAudio.y * 0.009;
    float x = cos(point.x * 2.1 + point.z * 3.4 + time * 0.54) * amplitude;
    float z = sin(point.z * 5.3 - point.x * 1.4 - time * 0.39) * amplitude;
    float radialDistance = length(point.xz - hero.xz);
    float eventRing = exp(-pow((radialDistance - uImpulseAge * 1.55) * 7.0, 2.0));
    float contact = sin(radialDistance * 13.0 - time * 0.9) * exp(-radialDistance * 1.8);
    z += (eventRing * uAudio.w * 0.027 + contact * 0.004) * uMotion;
    return normalize(vec3(x, 1.0, z));
}

vec3 worldColor(vec3 origin, vec3 direction, bool allowReflection) {
    float planeDistance = direction.y < -0.0005 ? -origin.y / direction.y : 80.0;
    bool nearObject = sphereHit(origin, direction, hero, heroRadius * 1.30) > 0.0;
    nearObject = nearObject || sphereHit(origin, direction, vec3(hero.x, 0.14, hero.z), heroRadius) > 0.0;
    vec2 hit = nearObject ? march(origin, direction, min(planeDistance, 16.0)) : vec2(0.0, -1.0);
    if (hit.y > 0.0 && hit.x < planeDistance) return shadeObject(origin, direction, hit);
    if (planeDistance >= 80.0) return environment(direction);
    vec3 point = origin + direction * planeDistance;
    vec3 normal = waterNormal(point);
    vec3 reflectedDirection = reflect(direction, normal);
    vec3 reflection = environment(reflectedDirection);
    if (allowReflection && sphereHit(point, reflectedDirection, hero, heroRadius * 1.30) > 0.0) {
        vec2 reflectedHit = march(point + normal * 0.01, reflectedDirection, 12.0);
        if (reflectedHit.y > 0.0) reflection = shadeObject(point + normal * 0.01, reflectedDirection, reflectedHit);
    }
    float viewFresnel = 0.08 + 0.92 * pow(1.0 - max(dot(-direction, normal), 0.0), 5.0);
    // Project the authored lake-bed color onto world coordinates, independently of the reflection.
    float horizonOffset = max(uSceneryHorizon - 0.371, 0.0);
    vec2 bedUv = vec2(0.5 + point.x * 0.055, 0.40 + horizonOffset + 0.66 * exp(-max(planeDistance - 2.0, 0.0) * 0.22));
    if (uLandscapeScenery != 0) {
        vec2 projected = projectToScenery(normalize(point - camera));
        float horizon = horizonOnScreen();
        bedUv = vec2(projected.x, uSceneryHorizon + (projected.y - horizon) * (1.0 - uSceneryHorizon) / (1.0 - horizon));
    }
    vec3 bed = source(bedUv) * vec3(0.91, 1.04, 1.045);
    vec3 color = mix(bed, reflection, 0.27 + viewFresnel * 0.40);
    float glint = pow(max(dot(reflectedDirection, normalize(vec3(0.52, 0.64, -0.38))), 0.0), 180.0);
    color += vec3(0.54, 0.44, 0.26) * glint;
    float ring = exp(-pow((length(point.xz - hero.xz) - uImpulseAge * 1.55) * 9.0, 2.0));
    color += vec3(0.06, 0.35, 0.38) * ring * uAudio.w * 0.18 * uMotion;
    float shadow = exp(-dot(point.xz - hero.xz, point.xz - hero.xz) * 3.5);
    color *= 1.0 - shadow * 0.16;
    float mist = 1.0 - exp(-max(planeDistance - 12.0, 0.0) * 0.065);
    color = mix(color, vec3(0.70, 0.78, 0.79), mist * 0.36);
    // Authored near shore is projected on the same plane; it is not a fixed screen vignette.
    vec2 shoreUv = vec2(0.5 + point.x * 0.20, bedUv.y);
    if (uLandscapeScenery != 0) shoreUv.x = bedUv.x;
    return mix(color, source(shoreUv), smoothstep(0.80, 0.93, bedUv.y));
}

vec3 lensColor(vec3 direction, vec3 underlying) {
    vec3 color = underlying;
    float nearest = 100.0;
    for (int index = 0; index < 5; ++index) {
        if (index >= uLensCount) break;
        // Orbit order begins with Player. Its real mineral volume replaces the duplicate lens.
        if (index == 0 && uOrbit > 0.5) continue;
        vec4 anchor = uLenses[index];
        vec3 axis = screenRay(anchor.xy);
        float depth = 2.65;
        vec3 center = camera + axis * depth;
        // Sphere angular radius matches the measured native control radius on either orientation.
        float radius = anchor.z * 2.0 * min(aspect, 1.0) * FOCAL * depth * dot(axis, forwardDirection);
        float hit = sphereHit(camera, direction, center, radius);
        if (hit <= 0.0 || hit > nearest) continue;
        nearest = hit;
        vec3 point = camera + direction * hit;
        vec3 normal = normalize(point - center);
        float facing = max(dot(-direction, normal), 0.0);
        vec3 exitPoint;
        float thickness;
        const float refractiveIndex = 1.26;
        vec3 exiting = sphereExitRay(point, normal, direction, center, radius, refractiveIndex, exitPoint, thickness);
        vec3 displaced = worldColor(exitPoint + exiting * 0.006, exiting, false);
        vec3 attenuation = exp(-vec3(0.016, 0.006, 0.003) * thickness);
        float baseReflectance = pow((refractiveIndex - 1.0) / (refractiveIndex + 1.0), 2.0);
        float fresnel = baseReflectance + (1.0 - baseReflectance) * pow(1.0 - facing, 5.0);
        vec3 reflected = environment(reflect(direction, normal));
        color = mix(displaced * attenuation, reflected, fresnel);
        vec3 lightDirection = normalize(vec3(-0.48, 0.72, 0.50));
        float sparkle = pow(max(dot(reflect(-lightDirection, normal), -direction), 0.0), 120.0);
        float edge = pow(1.0 - facing, 3.0);
        color += vec3(0.83, 0.94, 0.94) * sparkle * 0.53;
        color += vec3(0.28, 0.73, 0.77) * edge * (0.035 + anchor.w * 0.10);
    }
    return color;
}

vec3 connectedFilaments(vec3 direction, vec3 color) {
    if (uMotion < 0.5 || uOrbit < 0.01 || uLensCount < 2) return color;
    float heroFront = sphereHit(camera, direction, hero, heroRadius * 1.30);
    vec3 start = hero + vec3(0.0, -heroRadius * 0.22, heroRadius * 0.28);
    float energy = 0.0;
    for (int node = 1; node < 5; ++node) {
        if (node >= uLensCount) break;
        vec3 end = camera + screenRay(uLenses[node].xy) * 2.65;
        vec3 middle = mix(start, end, 0.5) + vec3(0.0, -0.18, 0.30);
        vec3 previous = start;
        for (int segment = 1; segment <= 8; ++segment) {
            float progress = float(segment) * 0.125;
            vec3 point = mix(mix(start, middle, progress), mix(middle, end, progress), progress);
            vec3 edge = point - previous;
            vec3 local = camera - previous;
            float alongRay = dot(direction, edge);
            float denominator = max(dot(edge, edge) - alongRay * alongRay, 0.00001);
            float alongEdge = clamp((dot(local, edge) - dot(local, direction) * alongRay) / denominator, 0.0, 1.0);
            vec3 closest = previous + edge * alongEdge;
            float distanceAlongRay = max(dot(closest - camera, direction), 0.0);
            float distanceToThread = length(closest - camera - direction * distanceAlongRay);
            if (heroFront < 0.0 || distanceAlongRay < heroFront + 0.004) {
                float pulse = pow(max(cos(progress * 9.0 - time * 0.60 - float(node)), 0.0), 8.0);
                float thread = exp(-distanceToThread * distanceToThread * 58000.0);
                energy += thread * (0.11 + pulse * (0.10 + uAudio.w * 0.14));
            }
            previous = point;
        }
    }
    return color + vec3(0.05, 0.50, 0.66) * min(energy, 0.75) * uOrbit;
}

vec3 orbitLight(vec3 direction, vec3 color) {
    if (uMotion < 0.5) return color;
    float extent = mix(0.12, 0.23, uOrbit) * (1.0 - smoothstep(0.0, 0.9, uRoute) * 0.55);
    float routeFocus = smoothstep(0.0, 0.9, uRoute) * (1.0 - uOrbit);
    vec2 center = vec2(0.5, mix(0.36, 0.23, routeFocus));
    if (uLensCount > 0) center = mix(center, uLenses[0].xy, uOrbit);
    vec2 screen = vec2(vUv.x, 1.0 - vUv.y);
    vec2 local = (screen - center) * vec2(aspect, 1.0);
    for (int path = 0; path < 2; ++path) {
        float phase = time * (0.20 + float(path) * 0.06) + float(path) * 2.1;
        vec2 ellipse = vec2(extent * 0.62, extent * 0.23);
        vec2 orbitPosition = vec2(cos(phase), sin(phase)) * ellipse;
        float pointGlow = exp(-dot(local - orbitPosition, local - orbitPosition) * 42000.0);
        float radial = length(local / ellipse);
        float trail = exp(-pow((radial - 1.0) * 145.0, 2.0));
        float angle = atan(local.y / ellipse.y, local.x / ellipse.x);
        float behind = sin(angle) < 0.0 ? 0.23 : 1.0;
        float tail = pow(max(0.0, cos(angle - phase)), 10.0);
        color += vec3(0.22, 0.70, 0.78) * (pointGlow * 0.35 + trail * tail * 0.10 * behind) * (0.6 + uAudio.z * 0.5);
    }
    return color;
}

void main() {
    aspect = uResolution.x / max(uResolution.y, 1.0);
    time = uTime * uMotion;
    // Calculate this once per fragment, outside every march and normal sample.
    // A restrained turn preserves the open chamber instead of periodically hiding the crystal.
    float rotation = 0.22 + sin(time * 0.08) * 0.12;
    float c = cos(rotation), s = sin(rotation);
    heroOrientation = mat2(c, -s, s, c);
    camera = vec3(sin(time * 0.045) * 0.035, 2.1, 5.8);
    forwardDirection = normalize(vec3(0.0, 0.80, 0.0) - camera);
    rightDirection = normalize(cross(forwardDirection, vec3(0.0, 1.0, 0.0)));
    upDirection = cross(rightDirection, forwardDirection);
    float routeFocus = smoothstep(0.0, 0.9, uRoute) * (1.0 - uOrbit);
    vec2 anchor = vec2(0.5, mix(0.36, 0.23, routeFocus));
    if (uLensCount > 0) anchor = mix(anchor, uLenses[0].xy, uOrbit);
    hero = camera + screenRay(anchor) * 5.55;
    hero.y += sin(time * 0.62) * 0.022 * (1.0 - routeFocus);
    heroRadius = mix(0.72, 0.31, routeFocus) * (1.0 + uAudio.x * 0.022);
    if (uLensCount > 0) {
        vec3 axis = screenRay(uLenses[0].xy);
        float orbitRadius = uLenses[0].z * 2.0 * min(aspect, 1.0) * FOCAL * 5.55 * dot(axis, forwardDirection);
        heroRadius = mix(heroRadius, orbitRadius, uOrbit);
    }
    vec3 direction = screenRay(vec2(vUv.x, 1.0 - vUv.y));
    vec3 color = worldColor(camera, direction, true);
    color = orbitLight(direction, color);
    color = connectedFilaments(direction, color);
    color = lensColor(direction, color);
    // Keep the world in daylight; native material roles supply localized text contrast.
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
