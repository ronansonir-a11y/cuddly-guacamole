package ru.dvor.horror;

/**
 * Минимальная математика 4x4 (column-major, как в OpenGL).
 * Углы в радианах. Операции translate/rotate/scale домножают матрицу справа: m = m * T.
 * Направление взгляда по yaw: f = (-sin yaw, 0, -cos yaw), вправо: r = (cos yaw, 0, -sin yaw).
 */
public final class M4 {
    private M4() {
    }

    public static void identity(float[] m) {
        for (int i = 0; i < 16; i++) m[i] = 0;
        m[0] = m[5] = m[10] = m[15] = 1;
    }

    /** o = a * b (o не должен совпадать с a или b). */
    public static void mul(float[] o, float[] a, float[] b) {
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k];
                o[c * 4 + r] = s;
            }
        }
    }

    public static void perspective(float[] m, float fovY, float aspect, float n, float f) {
        float t = (float) (1.0 / Math.tan(fovY * 0.5));
        for (int i = 0; i < 16; i++) m[i] = 0;
        m[0] = t / aspect;
        m[5] = t;
        m[10] = (f + n) / (n - f);
        m[11] = -1;
        m[14] = 2 * f * n / (n - f);
    }

    public static void lookAt(float[] m, float ex, float ey, float ez, float cx, float cy, float cz,
                              float ux, float uy, float uz) {
        float fx = cx - ex, fy = cy - ey, fz = cz - ez;
        float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl;
        fy /= fl;
        fz /= fl;
        float sx = fy * uz - fz * uy, sy = fz * ux - fx * uz, sz = fx * uy - fy * ux;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx /= sl;
        sy /= sl;
        sz /= sl;
        float vx = sy * fz - sz * fy, vy = sz * fx - sx * fz, vz = sx * fy - sy * fx;
        m[0] = sx; m[1] = vx; m[2] = -fx; m[3] = 0;
        m[4] = sy; m[5] = vy; m[6] = -fy; m[7] = 0;
        m[8] = sz; m[9] = vz; m[10] = -fz; m[11] = 0;
        m[12] = -(sx * ex + sy * ey + sz * ez);
        m[13] = -(vx * ex + vy * ey + vz * ez);
        m[14] = fx * ex + fy * ey + fz * ez;
        m[15] = 1;
    }

    public static void translate(float[] m, float x, float y, float z) {
        m[12] += m[0] * x + m[4] * y + m[8] * z;
        m[13] += m[1] * x + m[5] * y + m[9] * z;
        m[14] += m[2] * x + m[6] * y + m[10] * z;
        m[15] += m[3] * x + m[7] * y + m[11] * z;
    }

    public static void scale(float[] m, float x, float y, float z) {
        for (int i = 0; i < 4; i++) {
            m[i] *= x;
            m[4 + i] *= y;
            m[8 + i] *= z;
        }
    }

    public static void rotateY(float[] m, float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        for (int i = 0; i < 4; i++) {
            float c0 = m[i], c2 = m[8 + i];
            m[i] = c * c0 - s * c2;
            m[8 + i] = s * c0 + c * c2;
        }
    }

    public static void rotateX(float[] m, float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        for (int i = 0; i < 4; i++) {
            float c1 = m[4 + i], c2 = m[8 + i];
            m[4 + i] = c * c1 + s * c2;
            m[8 + i] = -s * c1 + c * c2;
        }
    }

    public static void rotateZ(float[] m, float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        for (int i = 0; i < 4; i++) {
            float c0 = m[i], c1 = m[4 + i];
            m[i] = c * c0 + s * c1;
            m[4 + i] = -s * c0 + c * c1;
        }
    }

    public static void copy(float[] dst, float[] src) {
        System.arraycopy(src, 0, dst, 0, 16);
    }

    /** Матрица билборда: плоскость XY (центр в начале) разворачивается к камере. */
    public static void billboard(float[] m, float px, float py, float pz,
                                 float rx, float ry, float rz, float ux, float uy, float uz,
                                 float sx, float sy) {
        float fx = ry * uz - rz * uy, fy = rz * ux - rx * uz, fz = rx * uy - ry * ux;
        m[0] = rx * sx; m[1] = ry * sx; m[2] = rz * sx; m[3] = 0;
        m[4] = ux * sy; m[5] = uy * sy; m[6] = uz * sy; m[7] = 0;
        m[8] = fx; m[9] = fy; m[10] = fz; m[11] = 0;
        m[12] = px; m[13] = py; m[14] = pz; m[15] = 1;
    }

    public static float wrapAngle(float a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }
}
