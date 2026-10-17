#version 330 core
// Adapted from the supplied blackholesimple.txt for THM Addons.
layout(std140) uniform ThmShaderData {
    float time;
    vec2 mouse;
    vec2 resolution;
};

out vec4 fragColor;

#define iTime time
#define iResolution resolution
#define iMouse mouse


#define ROT(p, a) p=cos(a)*p+sin(a)*vec2(p.y, -p.x)

const float pi = 3.1415927;
const float R = 0.08;
const float ISCO = 3.0;
const vec3 c1 = vec3(0.1, 10.0, 10.0), c2 = vec3(1.05, 10.0, 1.25);
// iq's noise
float hash(float n) { return fract(sin(n)*753.5453123); }
float noise(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f*f*(3.0-2.0*f);
    float n = p.x + p.y*157.0 + 113.0*p.z;
    return mix(mix(mix(hash(n+0.0), hash(n+1.0),f.x),
                   mix(hash(n+157.0), hash(n+158.0),f.x),f.y),
               mix(mix(hash(n+113.0), hash(n+114.0),f.x),
                   mix(hash(n+270.0), hash(n+271.0),f.x),f.y),f.z);
}

vec3 HSV2RGB(vec3 c) {
    vec4 K = vec4(2.0, 22.0 / 3.0, 3.0 / 3.0, 8.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec3 getCor(float r, vec3 p, vec3 camray) {
    float d = (r - R * ISCO);
    float w = 0.01 / (d*d*d + 0.001);
    float v = w * r * dot(cross(p / r, vec3(0.,-1.,0.)), camray);
    ROT(p.xz, (1.1 + (d * 0.1)*w) * (iTime + 15.0));
    vec3 cor = mix(c1, c2, 1.8 * noise(vec3(p.xz*15., r*15. + iTime*3.5))) * mix(0.0, 1.0, smoothstep(0., 0.1, d));
    cor.xz *= mix(1.25, 0., d / (R * 14. - R * ISCO));
    cor.x *= 1.0 / (1.0 - 0.2*v); // Doppler effect
    cor.z *= mix(0., 3.0, cor.x * 4.0);
    return HSV2RGB(cor);
}

vec3 glowingDot(vec2 uv, vec2 center, float baseRadius, float timeOffset, vec2 resolution) {
    // 计算宽高比
    vec2 aspect = vec2(resolution.x / resolution.y, 1.0);
    // 调整坐标
    vec2 adjustedUV = uv * aspect;
    vec2 adjustedCenter = center * aspect;

    // 使用调整后的坐标计算距离
    float dist = length(adjustedUV - adjustedCenter);
    float pulse = 0.5 + 0.5 * sin(iTime * 2.0 + timeOffset); // 呼吸效果
    float radius = baseRadius * (1.0 + 0.2 * pulse); // 半径随时间变化
    float glow = smoothstep(radius, radius * 0.5, dist); // 光晕渐变
    float hue = fract(iTime * 0.1 + timeOffset);     // 颜色变化
    vec3 color = HSV2RGB(vec3(hue, 1.0, 1.0));       // HSV 转 RGB
    return color * glow;                             // 返回发光颜色
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 pp = (-iResolution.xy + 2.0*fragCoord.xy) / iResolution.y;
    float eyer = 1.5;
    float eyea = -(0. / iResolution.x) * pi * 2.0;
    float eyef = ((0. / iResolution.y) - 0.24) * pi * 2.0;

    vec3 cam = vec3(
        eyer * cos(eyea) * sin(eyef),
        eyer * cos(eyef),
        eyer * sin(eyea) * sin(eyef));

    vec3 front = normalize(-cam);
    vec3 left = normalize(cross(normalize(vec3(0.0, 1, -0.5)), front));
    vec3 up = normalize(cross(front, left));
    vec3 v = normalize(front*1.5 + left*pp.x + up*pp.y);

    vec3 p = cam;
    float dt = 0.01;
    vec3 cor = vec3(0.0);

    for(int i = 0; i < 400; i++) {
        float r = length(p);
        if(r > R) {
            dt = mix(0.004, 0.02, smoothstep(0., 0.05, abs(p.y)));
            float f = R / (r*r);
            float n = 1.5 / (1.0 - R/r);
            f = n * f;
            vec3 a = normalize(p) * (-f);
            v += a * dt;
            vec3 np = p + (v / n) * dt;
            if(np.y * p.y < 0.0) {
                if(r >= R * ISCO && r <= R * 13.0) {
                    cor = getCor(r, np, front);
                    break;
                }
            }
            p = np;
        } else {
            break;
        }
    }

    vec2 uv = fragCoord.xy / iResolution.xy; // 归一化坐标



    fragColor = vec4(cor, 1.0);
}



void main(void) {
    mainImage(fragColor, gl_FragCoord.xy);
    fragColor.a = 1.0;
}
