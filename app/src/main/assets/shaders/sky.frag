#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform vec3 uMoonDir;
uniform float uTime;
uniform float uFear;
uniform vec2 uRes;
varying vec3 vDir;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

void main() {
    vec3 d = normalize(vDir);
    float h = clamp(d.y, 0.0, 1.0);
    vec3 col = mix(vec3(0.05, 0.075, 0.14), vec3(0.008, 0.014, 0.04), pow(h, 0.45));

    // звёзды
    vec3 g = d * 220.0;
    vec3 cell = floor(g);
    float s = hash(cell);
    float r = length(fract(g) - 0.5);
    float tw = 0.65 + 0.35 * sin(uTime * 2.0 + s * 60.0);
    col += vec3(0.9, 0.95, 1.0) * step(0.9975, s) * smoothstep(0.38, 0.0, r) * tw * smoothstep(0.04, 0.35, d.y);

    // луна и гало
    float md = dot(d, normalize(uMoonDir));
    col += vec3(0.92, 0.96, 1.0) * smoothstep(0.99885, 0.9992, md);
    col += vec3(0.22, 0.3, 0.5) * pow(max(md, 0.0), 90.0) * 0.5;

    vec2 sc = gl_FragCoord.xy / uRes - 0.5;
    col *= clamp(1.0 - dot(sc, sc) * (1.1 + uFear * 1.8), 0.0, 1.0);
    float n = fract(sin(dot(gl_FragCoord.xy + vec2(uTime * 37.0, uTime * 17.0), vec2(12.9898, 78.233))) * 43758.5453);
    col += (n - 0.5) * (0.03 + 0.05 * uFear);
    gl_FragColor = vec4(col, 1.0);
}
