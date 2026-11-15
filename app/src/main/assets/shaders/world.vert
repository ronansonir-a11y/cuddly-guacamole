uniform mat4 uVP;
uniform mat4 uModel;
attribute vec3 aPos;
attribute vec3 aNrm;
attribute vec2 aUv;
attribute vec3 aCol;
varying vec3 vPos;
varying vec3 vNrm;
varying vec2 vUv;
varying vec3 vCol;

void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vPos = w.xyz;
    vNrm = (uModel * vec4(aNrm, 0.0)).xyz;
    vUv = aUv;
    vCol = aCol;
    gl_Position = uVP * w;
}
