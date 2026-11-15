#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTex;
uniform sampler2D uDetail;
uniform vec3 uTint;
uniform float uEmis;
uniform float uCut;
uniform float uDetailAmt;
uniform vec3 uEye;
uniform vec3 uFlashPos;
uniform vec3 uFlashDir;
uniform float uFlash;
uniform vec3 uLightPos[8];
uniform vec3 uLightCol[8];
uniform vec3 uAmb;
uniform vec3 uFogCol;
uniform float uFogDen;
uniform float uFear;
uniform float uTime;
uniform vec2 uRes;

varying vec3 vPos;
varying vec3 vNrm;
varying vec2 vUv;
varying vec3 vCol;

void main() {
    vec4 t = texture2D(uTex, vUv);
    if (t.a < uCut) discard;
    vec3 N = normalize(vNrm);
    if (!gl_FrontFacing) N = -N;

    vec3 albedo = t.rgb * vCol * uTint;
    if (uDetailAmt > 0.0) {
        float dt = texture2D(uDetail, vPos.xz * 0.13 + vPos.y * vec2(0.07, 0.11)).r;
        albedo *= mix(1.0, dt * 1.25, uDetailAmt);
    }

    // лунный свет сверху
    vec3 light = uAmb * (0.5 + 0.5 * (N.y * 0.5 + 0.5));

    // фонарик героя: конус
    vec3 Lf = uFlashPos - vPos;
    float df = length(Lf);
    Lf /= df;
    float cosA = dot(-Lf, uFlashDir);
    float spot = smoothstep(0.80, 0.95, cosA);
    float attF = 1.0 / (1.0 + 0.05 * df + 0.02 * df * df);
    light += vec3(1.0, 0.95, 0.82) * 2.6 * spot * attF * (0.25 + 0.75 * max(dot(N, Lf), 0.0)) * uFlash;

    // точечные: ключи, фонари, лампа подъезда
    for (int i = 0; i < 8; i++) {
        vec3 Lp = uLightPos[i] - vPos;
        float dp = length(Lp);
        Lp /= dp;
        float att = 1.0 / (1.0 + 0.08 * dp + 0.04 * dp * dp);
        light += uLightCol[i] * att * (0.3 + 0.7 * max(dot(N, Lp), 0.0));
    }

    vec3 col = albedo * (light + uEmis);

    // туман
    float dist = length(uEye - vPos);
    float fogF = 1.0 - exp(-dist * dist * uFogDen * uFogDen);
    col = mix(col, uFogCol * t.a, fogF);

    // виньетка, зерно, обесцвечивание от страха
    vec2 sc = gl_FragCoord.xy / uRes - 0.5;
    col *= clamp(1.0 - dot(sc, sc) * (1.1 + uFear * 1.8), 0.0, 1.0);
    float lum = dot(col, vec3(0.3, 0.59, 0.11));
    col = mix(col, vec3(lum * 1.15, lum * 0.85, lum * 0.85), 0.35 * uFear);
    float n = fract(sin(dot(gl_FragCoord.xy + vec2(uTime * 37.0, uTime * 17.0), vec2(12.9898, 78.233))) * 43758.5453);
    col += (n - 0.5) * (0.03 + 0.05 * uFear) * t.a;

    gl_FragColor = vec4(col, t.a);
}
