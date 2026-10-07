#version 300 es
precision highp float;
precision highp sampler2D;

in vec2 vUv;
out vec4 fragColor;

//#include lib_scene_uniforms
//#include lib_palette
//#include lib_scene_grade

// Prismatic Passage: actual world-space beads, helical filaments and folded
// sculptures. Camera pose belongs to SpatialCameraDirector, never uTime*audio.
// Each object has its own world anchor, so approaching it reveals parallax and
// occlusion instead of keeping a screen-space emblem pinned to the vanishing point.
uniform vec3 uCameraPosition;
uniform vec3 uCameraRight;
uniform vec3 uCameraUp;
uniform vec3 uCameraForward;
uniform vec2 uCorridorPhase;
uniform vec4 uSpatialBands;
uniform float uSpatialForm;

const float PASSAGE_TAU = 6.28318530718;
const float PASSAGE_PERIOD = 96.0; // SpatialCameraDirector::kCorridorPeriod
const float PASSAGE_RADIUS = 2.1; // SpatialCameraDirector::kCorridorRadius
const float PASSAGE_FAR = 26.0;
const float PASSAGE_RING_SPACING = 0.8; // 120 rings per world repeat
const float PASSAGE_OBJECT_SPACING = 12.0; // eight independent world anchors
const int PASSAGE_STEPS = 128;

vec2 passageCenter(float z) {
    // Must match SpatialCameraDirector::corridorCenter, including every harmonic.
    float phase = z * (PASSAGE_TAU / PASSAGE_PERIOD);
    return vec2(
        0.55 * sin(phase + uCorridorPhase.x) + 0.18 * sin(2.0 * phase + uCorridorPhase.y),
        0.42 * sin(phase + uCorridorPhase.y) + 0.16 * sin(3.0 * phase + uCorridorPhase.x));
}

mat2 rotatePlane(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat2(c, -s, s, c);
}

float smoothJoin(float a, float b, float width) {
    float h = clamp(0.5 + 0.5 * (b - a) / width, 0.0, 1.0);
    return mix(b, a, h) - width * h * (1.0 - h);
}

float sculpture(vec3 p, float cell) {
    float anchor = cell * PASSAGE_OBJECT_SPACING;
    // The anchor is fixed in the world. Only the form turns and breathes.
    float angle = anchor * PASSAGE_TAU / PASSAGE_PERIOD + uCorridorPhase.x;
    p.xy -= passageCenter(anchor) + 1.05 * vec2(cos(angle), sin(angle));
    p.z -= anchor;
    p.xz = rotatePlane(uSpatialForm + angle) * p.xz;
    p.yz = rotatePlane(0.5 * sin(uSpatialForm) + angle) * p.yz;
    float fold = clamp(0.6 * (0.5 + 0.5 * sin(uSpatialForm + angle + uSpatialBands.y * 0.45))
                       + 0.4 * uMorph, 0.0, 1.0);
    float core = mix(length(p) - 0.24,
                     (dot(abs(p), vec3(1.0)) - 0.47) * 0.57735027, fold);
    float ringRadius = 0.39 + 0.035 * sin(uSpatialForm + uSpatialBands.x);
    float tube = 0.055 + 0.008 * min(uSpatialBands.z, 1.0);
    float ringA = length(vec2(length(p.xy) - ringRadius, p.z)) - tube;
    float ringB = length(vec2(length(p.yz) - ringRadius, p.x)) - tube;
    float ringC = length(vec2(length(p.xz) - ringRadius, p.y)) - tube;
    return smoothJoin(core, min(ringA, min(ringB, ringC)), 0.09);
}

// x: conservative signed distance; y: material (bead=1, filament=2,
// folded wall=3, sculpture=4). Two adjacent sculptures are unnecessary: their
// bounding radii are <0.6 and the cell boundary is six units from either centre.
vec2 sceneDistance(vec3 world) {
    vec3 p = world;
    p.xy -= passageCenter(p.z);
    float radius = length(p.xy);
    float angle = radius > 0.00001 ? atan(p.y, p.x) : 0.0;
    // A whole number of twists per 96 units preserves the bounded travel seam.
    float helix = angle + p.z * (3.0 * PASSAGE_TAU / PASSAGE_PERIOD);
    float sector = PASSAGE_TAU / 18.0;
    float localAngle = mod(helix + 0.5 * sector, sector) - 0.5 * sector;
    vec2 radialCell = vec2(radius * cos(localAngle) - PASSAGE_RADIUS, radius * sin(localAngle));
    float ringZ = mod(p.z + 0.5 * PASSAGE_RING_SPACING, PASSAGE_RING_SPACING) - 0.5 * PASSAGE_RING_SPACING;
    float pearl = 0.135 + 0.022 * sin(uSpatialForm + p.z * PASSAGE_TAU / PASSAGE_PERIOD)
                  + 0.025 * min(uSpatialBands.x, 1.0);
    float beads = length(vec3(radialCell, ringZ)) - pearl;
    float threads = length(radialCell) - 0.018;
    vec2 result = beads < threads ? vec2(beads, 1.0) : vec2(threads, 2.0);

    // Nested wall scales are spatial relief, not a flat polar background.
    float depthPhase = p.z * PASSAGE_TAU / PASSAGE_PERIOD;
    float foldedWall = 2.48 + 0.12 * sin(6.0 * angle + 7.0 * depthPhase + sin(uSpatialForm))
                           + 0.055 * sin(18.0 * angle - 14.0 * depthPhase);
    float wall = foldedWall - radius;
    if (wall < result.x) result = vec2(wall, 3.0);
    float objectCell = floor(world.z / PASSAGE_OBJECT_SPACING + 0.5);
    float objectDistance = sculpture(world, objectCell);
    if (objectDistance < result.x) result = vec2(objectDistance, 4.0);
    // Bound the corridor warp, helix and wall angular displacement together.
    // SmoothJoin cannot increase the maximum component Lipschitz bound.
    result.x *= 0.43;
    return result;
}

vec3 surfaceNormal(vec3 point, float epsilon) {
    vec2 k = vec2(1.0, -1.0);
    vec3 n = k.xyy * sceneDistance(point + k.xyy * epsilon).x
           + k.yyx * sceneDistance(point + k.yyx * epsilon).x
           + k.yxy * sceneDistance(point + k.yxy * epsilon).x
           + k.xxx * sceneDistance(point + k.xxx * epsilon).x;
    float magnitude = dot(n, n);
    return magnitude > 1e-12 ? n * inversesqrt(magnitude) : vec3(0.0, 0.0, -1.0);
}

void main() {
    // Existing explicit framing/grade controls retain their normal semantics;
    // the actual camera basis/origin always comes from the native director.
    vec2 uv = view();
    vec3 origin = uCameraPosition;
    vec3 ray = normalize(uCameraRight * uv.x + uCameraUp * uv.y + uCameraForward * 1.45);
    float distance = 0.015;
    float hit = -1.0;
    float material = 0.0;
    for (int i = 0; i < PASSAGE_STEPS; ++i) {
        if (float(i) >= uSteps || distance > PASSAGE_FAR) break;
        vec2 sampleDistance = sceneDistance(origin + ray * distance);
        float epsilon = 0.0005 + 0.00055 * distance;
        if (sampleDistance.x < epsilon) {
            hit = distance;
            material = sampleDistance.y;
            break;
        }
        distance += max(sampleDistance.x, epsilon * 0.6);
    }

    vec3 dark = mix(vec3(0.002, 0.003, 0.009), pal(0.66) * 0.022, clamp(uv.y * 0.2 + 0.4, 0.0, 1.0));
    vec3 colour = dark;
    if (hit > 0.0) {
        vec3 point = origin + ray * hit;
        vec3 normal = surfaceNormal(point, max(0.0006, hit * 0.00065));
        vec2 around = point.xy - passageCenter(point.z);
        float angle = atan(around.y, around.x);
        float depthPhase = point.z * PASSAGE_TAU / PASSAGE_PERIOD;
        float hue = 0.17 * sin(depthPhase + uCorridorPhase.y) + 0.23 * cos(angle * 3.0);
        if (material > 3.5) hue += 0.4;
        vec3 albedo = pal(hue);
        vec3 light = normalize(uCameraRight * -0.45 + uCameraUp * 0.6 - uCameraForward * 0.7);
        float diffuse = max(dot(normal, light), 0.0);
        vec3 halfway = normalize(light - ray);
        float gloss = pow(max(dot(normal, halfway), 0.0), material > 2.5 && material < 3.5 ? 20.0 : 62.0);
        float rim = pow(1.0 - max(dot(normal, -ray), 0.0), 3.0);
        float veins = pow(0.5 + 0.5 * sin(angle * 24.0 + 20.0 * depthPhase), 8.0);
        float sheen = 0.5 + 0.5 * cos(12.0 * dot(normal, -ray) + uSpatialForm);
        float wallShade = material > 2.5 && material < 3.5 ? 0.34 : 1.0;
        colour = albedo * (0.07 + 0.46 * diffuse) * wallShade;
        colour += mix(albedo, vec3(1.0), 0.65) * gloss * (0.38 + 0.12 * uSpatialBands.z);
        colour += pal(hue + 0.22 + sheen * 0.12) * rim * 0.32;
        colour += albedo * veins * (0.02 + 0.09 * min(uSpatialBands.y, 1.0)) * wallShade;
        if (material > 1.5 && material < 2.5) colour += pal(hue + 0.3) * (0.18 + 0.1 * min(uSpatialBands.z, 1.0));
        colour = mix(colour, dark, 1.0 - exp(-hit * 0.055));
    }
    // Preserve dark space and coloured highlights; no scene-wide beat flashes.
    colour = vec3(1.0) - exp(-max(colour, vec3(0.0)) * 1.3);
    colour *= 1.0 - 0.18 * smoothstep(0.4, 2.0, length(uv));
    fragColor = vec4(grade(colour), 1.0);
}
