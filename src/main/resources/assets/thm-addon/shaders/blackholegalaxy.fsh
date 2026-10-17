#version 330 core
// Original procedural black hole and galaxy for THM Addons.
layout(std140) uniform ThmShaderData {
    float time;
    vec2 mouse;
    vec2 resolution;
};

out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

mat2 rotate(float angle) {
    float c = cos(angle), s = sin(angle);
    return mat2(c, -s, s, c);
}

float hash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(cell), hash(cell + vec3(1, 0, 0)), f.x),
                   mix(hash(cell + vec3(0, 1, 0)), hash(cell + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(cell + vec3(0, 0, 1)), hash(cell + vec3(1, 0, 1)), f.x),
                   mix(hash(cell + vec3(0, 1, 1)), hash(cell + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float result = 0.0;
    float amplitude = 0.55;
    for (int octave = 0; octave < 4; octave++) {
        result += amplitude * noise(p);
        p = p * 2.03 + vec3(17.3, 9.2, 13.7);
        amplitude *= 0.5;
    }
    return result;
}

vec3 stars(vec2 uv, float scale) {
    vec2 grid = uv * vec2(scale, scale * 0.5);
    vec2 cell = floor(grid);
    float seed = hash(vec3(cell, scale));
    vec2 center = vec2(hash(vec3(cell, 1.7)), hash(vec3(cell, 8.3))) * 0.6 + 0.2;
    vec2 delta = fract(grid) - center;
    float distanceSquared = dot(delta, delta);
    float core = exp(-distanceSquared * mix(160.0, 420.0, seed));
    float halo = exp(-distanceSquared * 28.0) * 0.055;
    float twinkle = 0.85 + 0.15 * sin(time * 0.7 + seed * TAU);
    vec3 tint = mix(vec3(0.48, 0.66, 1.0), vec3(1.0, 0.79, 0.51), seed);
    return tint * (core + halo) * step(0.965, seed) * (0.6 + seed * 1.8) * twinkle;
}

vec3 galaxy(vec3 direction) {
    vec3 normal = normalize(vec3(0.28, 0.9, -0.32));
    vec3 across = normalize(vec3(normal.z, 0.0, -normal.x));
    vec3 p = vec3(dot(direction, across), dot(direction, normal), dot(direction, cross(across, normal)));
    float latitude = p.y;
    float band = exp(-latitude * latitude * 11.0);
    float clouds = fbm(p * 8.0 + vec3(0.0, time * 0.003, 0.0));
    float filaments = fbm(p * 32.0 + vec3(clouds * 5.0));
    float dust = smoothstep(0.34, 0.68, filaments) * exp(-latitude * latitude * 55.0);
    vec3 mist = mix(vec3(0.018, 0.032, 0.11), vec3(0.14, 0.028, 0.12), smoothstep(0.32, 0.64, clouds));
    mist = mix(mist, vec3(0.02, 0.11, 0.16), smoothstep(0.52, 0.72, filaments));
    vec3 color = vec3(0.0015, 0.002, 0.008) + mist * band * smoothstep(0.25, 0.72, clouds) * (0.15 + filaments * 0.8);
    color += vec3(0.38, 0.27, 0.19) * pow(clouds, 4.0) * band;
    color *= 1.0 - 0.8 * dust;
    vec2 skyUV = vec2(atan(p.z, p.x) / TAU + 0.5, asin(clamp(p.y, -1.0, 1.0)) / PI + 0.5);
    color += stars(skyUV, 390.0) + stars(skyUV + vec2(0.137, 0.293), 780.0) * 0.7;
    return color;
}

vec4 disk(vec3 p, vec3 direction, float pathLength) {
    float radius = length(p.xz);
    float inner = smoothstep(2.45, 2.9, radius);
    float outer = 1.0 - smoothstep(7.0, 10.0, radius);
    if (inner * outer <= 0.0) return vec4(0.0);

    float angle = atan(p.z, p.x);
    float orbit = time * 1.8 / pow(radius, 1.25);
    vec2 flow = rotate(orbit + 2.8 * log(radius)) * p.xz;
    float turbulence = fbm(vec3(flow * 2.8, time * 0.12));
    float fine = noise(vec3(flow * 15.0, time * 0.35));
    float spiral = 0.5 + 0.5 * sin(radius * 24.0 - angle * 3.0 + turbulence * 6.0 - orbit * 3.0);
    float filaments = mix(0.35, 1.3, turbulence) * mix(0.65, 1.15, spiral) * mix(0.75, 1.25, fine);
    float heat = pow(2.5 / radius, 0.9);
    vec3 temperature = mix(vec3(0.38, 0.13, 0.42), vec3(1.8, 0.48, 0.09), smoothstep(0.22, 0.5, heat));
    temperature = mix(temperature, vec3(2.6, 2.05, 1.4), smoothstep(0.55, 1.0, heat));
    vec3 tangent = vec3(-p.z, 0.0, p.x) / radius;
    float doppler = pow(clamp(1.0 / (1.0 - 0.42 * dot(tangent, -direction)), 0.65, 1.65), 3.0);
    float alpha = 1.0 - exp(-inner * outer * filaments * pathLength * 1.8);
    return vec4(temperature * filaments * doppler * (0.65 + heat * 2.3), alpha);
}

void main(void) {
    vec2 uv = (gl_FragCoord.xy - resolution * 0.5) / max(resolution.y, 1.0);
    uv = rotate(-0.13 + 0.025 * sin(time * 0.065)) * uv;
    float azimuth = 0.3 + time * 0.025;
    float elevation = 0.16 + 0.025 * sin(time * 0.09);
    vec3 origin = vec3(sin(azimuth) * cos(elevation), sin(elevation), cos(azimuth) * cos(elevation));
    origin *= 19.5 + 0.4 * sin(time * 0.06);
    vec3 forward = normalize(-origin);
    vec3 right = normalize(cross(forward, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(right, forward);
    vec3 direction = normalize(forward * 1.12 + right * uv.x + up * uv.y);
    vec3 position = origin;
    vec3 angularMomentum = cross(origin, direction);
    float impactSquared = dot(angularMomentum, angularMomentum);
    vec3 color = vec3(0.0);
    float transmission = 1.0;
    float closest = length(origin);
    bool captured = false;

    // Bounded curved-ray integration; only disk crossings evaluate turbulent emission.
    for (int step = 0; step < 180; step++) {
        float radius = length(position);
        closest = min(closest, radius);
        if (radius < 1.0) {
            captured = true;
            break;
        }
        if (radius > 32.0 || transmission < 0.015) break;
        float stride = clamp(radius * 0.065, 0.045, 0.85);
        float radiusSquared = radius * radius;
        vec3 acceleration = -1.5 * impactSquared * position / (radiusSquared * radiusSquared * radius);
        vec3 nextDirection = normalize(direction + acceleration * stride);
        vec3 next = position + normalize(direction + nextDirection) * stride;
        float thickness = 0.08 + 0.018 * radius;
        float dy = next.y - position.y;
        float entry = 0.0, exit = 1.0;
        if (abs(dy) > 0.00001) {
            float a = (-thickness - position.y) / dy;
            float b = (thickness - position.y) / dy;
            entry = max(0.0, min(a, b));
            exit = min(1.0, max(a, b));
        } else if (abs(position.y) > thickness) {
            exit = -1.0;
        }
        if (entry < exit && radius < 11.0) {
            vec3 samplePosition = mix(position, next, (entry + exit) * 0.5);
            vec4 emission = disk(samplePosition, direction, (exit - entry) * stride);
            color += transmission * emission.rgb * emission.a;
            transmission *= 1.0 - emission.a;
        }
        position = next;
        direction = nextDirection;
    }

    if (!captured && transmission > 0.015) color += transmission * galaxy(direction);
    float photonRing = captured ? 0.0 : exp(-abs(closest - 1.52) * 22.0);
    color += transmission * photonRing * vec3(0.7, 0.36, 0.13) * 0.55;
    color *= 1.15;
    color = vec3(1.0) - exp(-color);
    color = pow(max(color, vec3(0.0)), vec3(0.4545));
    float vignette = 1.0 - 0.26 * smoothstep(0.25, 1.3, length(uv));
    fragColor = vec4(color * vignette, 1.0);
}
