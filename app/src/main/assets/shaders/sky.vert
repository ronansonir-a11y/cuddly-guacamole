attribute vec2 aPos;
uniform vec3 uCamR;
uniform vec3 uCamU;
uniform vec3 uCamF;
uniform float uTanHalf;
uniform float uAspect;
varying vec3 vDir;

void main() {
    vDir = uCamF + uCamR * (aPos.x * uTanHalf * uAspect) + uCamU * (aPos.y * uTanHalf);
    gl_Position = vec4(aPos, 0.999, 1.0);
}
