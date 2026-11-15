precision mediump float;

uniform sampler2D uTex;
uniform vec4 uColor;
varying vec2 vUv;

void main() {
    float a = texture2D(uTex, vUv).a;
    gl_FragColor = vec4(uColor.rgb * (a * uColor.a), 1.0);
}
