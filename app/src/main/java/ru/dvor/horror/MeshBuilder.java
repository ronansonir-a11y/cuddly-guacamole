package ru.dvor.horror;

import java.util.Arrays;

/**
 * Собирает геометрию. Вершина = pos(3) nrm(3) uv(2) color(3) = 11 float.
 * Текстурная координата v=0 — верх картинки. Можно задать матрицу трансформации для всех следующих примитивов.
 */
public final class MeshBuilder {
    public static final int STRIDE = 11;

    private float[] d = new float[1 << 14];
    private int n;
    private final float[] xf = new float[16];
    private boolean hasXf;

    public MeshBuilder() {
        M4.identity(xf);
    }

    public void identity() {
        M4.identity(xf);
        hasXf = false;
    }

    public void transform(float[] m) {
        M4.copy(xf, m);
        hasXf = true;
    }

    public int vertexCount() {
        return n / STRIDE;
    }

    public float[] toArray() {
        return Arrays.copyOf(d, n);
    }

    private void put(float x, float y, float z, float nx, float ny, float nz,
                     float u, float v, float r, float g, float b) {
        if (n + STRIDE > d.length) d = Arrays.copyOf(d, d.length * 2);
        if (hasXf) {
            float tx = xf[0] * x + xf[4] * y + xf[8] * z + xf[12];
            float ty = xf[1] * x + xf[5] * y + xf[9] * z + xf[13];
            float tz = xf[2] * x + xf[6] * y + xf[10] * z + xf[14];
            float qx = xf[0] * nx + xf[4] * ny + xf[8] * nz;
            float qy = xf[1] * nx + xf[5] * ny + xf[9] * nz;
            float qz = xf[2] * nx + xf[6] * ny + xf[10] * nz;
            float l = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
            if (l > 1e-6f) {
                qx /= l;
                qy /= l;
                qz /= l;
            }
            x = tx;
            y = ty;
            z = tz;
            nx = qx;
            ny = qy;
            nz = qz;
        }
        d[n++] = x; d[n++] = y; d[n++] = z;
        d[n++] = nx; d[n++] = ny; d[n++] = nz;
        d[n++] = u; d[n++] = v;
        d[n++] = r; d[n++] = g; d[n++] = b;
    }

    /**
     * Четырёхугольник a,b,c,d против часовой стрелки (если смотреть с лицевой стороны).
     * UV: a=(u0,v1) b=(u1,v1) c=(u1,v0) d=(u0,v0); v0 — верх картинки.
     */
    public void quad(float ax, float ay, float az, float bx, float by, float bz,
                     float cx, float cy, float cz, float dx, float dy, float dz,
                     float u0, float v0, float u1, float v1, float r, float g, float b) {
        float e1x = bx - ax, e1y = by - ay, e1z = bz - az;
        float e2x = dx - ax, e2y = dy - ay, e2z = dz - az;
        float nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-9f) return;
        nx /= l;
        ny /= l;
        nz /= l;
        put(ax, ay, az, nx, ny, nz, u0, v1, r, g, b);
        put(bx, by, bz, nx, ny, nz, u1, v1, r, g, b);
        put(cx, cy, cz, nx, ny, nz, u1, v0, r, g, b);
        put(ax, ay, az, nx, ny, nz, u0, v1, r, g, b);
        put(cx, cy, cz, nx, ny, nz, u1, v0, r, g, b);
        put(dx, dy, dz, nx, ny, nz, u0, v0, r, g, b);
    }

    /** Вертикальная стена от (x0,z0) до (x1,z1), от y0 до y1; текстура повторяется каждые tileW x tileH метров. */
    public void wall(float x0, float z0, float x1, float z1, float y0, float y1,
                     float tileW, float tileH, float r, float g, float b) {
        float len = (float) Math.sqrt((x1 - x0) * (x1 - x0) + (z1 - z0) * (z1 - z0));
        float u1 = tileW > 0 ? len / tileW : 1f;
        float v1 = tileH > 0 ? (y1 - y0) / tileH : 1f;
        quad(x0, y0, z0, x1, y0, z1, x1, y1, z1, x0, y1, z0, 0, 0, u1, v1, r, g, b);
    }

    /** Горизонтальный прямоугольник на высоте y (смотрит вверх); текстура привязана к мировым координатам. */
    public void floor(float x0, float z0, float x1, float z1, float y, float tile, float r, float g, float b) {
        float t = tile > 0 ? tile : 1f;
        quad(x0, y, z1, x1, y, z1, x1, y, z0, x0, y, z0, x0 / t, z0 / t, x1 / t, z1 / t, r, g, b);
    }

    /** Параллелепипед по центру и размерам; tile — метров на повтор текстуры (<=0: один раз на грань). */
    public void box(float cx, float cy, float cz, float sx, float sy, float sz,
                    float r, float g, float b, float tile) {
        float x0 = cx - sx / 2, x1 = cx + sx / 2, y0 = cy - sy / 2, y1 = cy + sy / 2, z0 = cz - sz / 2, z1 = cz + sz / 2;
        float ux = tile > 0 ? sx / tile : 1f, uy = tile > 0 ? sy / tile : 1f, uz = tile > 0 ? sz / tile : 1f;
        quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 0, 0, uz, uy, r, g, b);
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, 0, 0, uz, uy, r, g, b);
        quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 0, ux, uz, r, g, b);
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, 0, ux, uz, r, g, b);
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, ux, uy, r, g, b);
        quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, ux, uy, r, g, b);
    }

    /** Вертикальный цилиндр с основанием в (cx,y0,cz). */
    public void cylinder(float cx, float y0, float cz, float radius, float height, int seg,
                         float r, float g, float b, boolean cap) {
        float y1 = y0 + height;
        for (int i = 0; i < seg; i++) {
            float a0 = (float) (2 * Math.PI * i / seg), a1 = (float) (2 * Math.PI * (i + 1) / seg);
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float u0 = (float) i / seg, u1 = (float) (i + 1) / seg;
            put(cx + c0 * radius, y0, cz + s0 * radius, c0, 0, s0, u0, 1, r, g, b);
            put(cx + c1 * radius, y0, cz + s1 * radius, c1, 0, s1, u1, 1, r, g, b);
            put(cx + c1 * radius, y1, cz + s1 * radius, c1, 0, s1, u1, 0, r, g, b);
            put(cx + c0 * radius, y0, cz + s0 * radius, c0, 0, s0, u0, 1, r, g, b);
            put(cx + c1 * radius, y1, cz + s1 * radius, c1, 0, s1, u1, 0, r, g, b);
            put(cx + c0 * radius, y1, cz + s0 * radius, c0, 0, s0, u0, 0, r, g, b);
            if (cap) {
                put(cx, y1, cz, 0, 1, 0, 0.5f, 0.5f, r, g, b);
                put(cx + c1 * radius, y1, cz + s1 * radius, 0, 1, 0, 0.5f + c1 * 0.5f, 0.5f + s1 * 0.5f, r, g, b);
                put(cx + c0 * radius, y1, cz + s0 * radius, 0, 1, 0, 0.5f + c0 * 0.5f, 0.5f + s0 * 0.5f, r, g, b);
            }
        }
    }

    /** Эллипсоид (UV-сфера): u вокруг оси Y, v сверху вниз. */
    public void sphere(float cx, float cy, float cz, float rx, float ry, float rz, int lon, int lat,
                       float r, float g, float b) {
        for (int j = 0; j < lat; j++) {
            float p0 = (float) (Math.PI * j / lat), p1 = (float) (Math.PI * (j + 1) / lat);
            for (int i = 0; i < lon; i++) {
                float t0 = (float) (2 * Math.PI * i / lon), t1 = (float) (2 * Math.PI * (i + 1) / lon);
                sv(cx, cy, cz, rx, ry, rz, p0, t0, (float) i / lon, (float) j / lat, r, g, b);
                sv(cx, cy, cz, rx, ry, rz, p1, t0, (float) i / lon, (float) (j + 1) / lat, r, g, b);
                sv(cx, cy, cz, rx, ry, rz, p1, t1, (float) (i + 1) / lon, (float) (j + 1) / lat, r, g, b);
                sv(cx, cy, cz, rx, ry, rz, p0, t0, (float) i / lon, (float) j / lat, r, g, b);
                sv(cx, cy, cz, rx, ry, rz, p1, t1, (float) (i + 1) / lon, (float) (j + 1) / lat, r, g, b);
                sv(cx, cy, cz, rx, ry, rz, p0, t1, (float) (i + 1) / lon, (float) j / lat, r, g, b);
            }
        }
    }

    private void sv(float cx, float cy, float cz, float rx, float ry, float rz, float phi, float th,
                    float u, float v, float r, float g, float b) {
        float nx = (float) (Math.sin(phi) * Math.cos(th)), ny = (float) Math.cos(phi), nz = (float) (Math.sin(phi) * Math.sin(th));
        put(cx + nx * rx, cy + ny * ry, cz + nz * rz, nx, ny, nz, u, v, r, g, b);
    }

    /** Плоское кольцо, смотрит вверх. */
    public void ring(float cx, float cy, float cz, float rIn, float rOut, int seg, float r, float g, float b) {
        for (int i = 0; i < seg; i++) {
            float a0 = (float) (2 * Math.PI * i / seg), a1 = (float) (2 * Math.PI * (i + 1) / seg);
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            put(cx + c0 * rIn, cy, cz + s0 * rIn, 0, 1, 0, 0, 0, r, g, b);
            put(cx + c0 * rOut, cy, cz + s0 * rOut, 0, 1, 0, 0, 1, r, g, b);
            put(cx + c1 * rOut, cy, cz + s1 * rOut, 0, 1, 0, 1, 1, r, g, b);
            put(cx + c0 * rIn, cy, cz + s0 * rIn, 0, 1, 0, 0, 0, r, g, b);
            put(cx + c1 * rOut, cy, cz + s1 * rOut, 0, 1, 0, 1, 1, r, g, b);
            put(cx + c1 * rIn, cy, cz + s1 * rIn, 0, 1, 0, 1, 0, r, g, b);
        }
    }

    /** Две пересекающиеся вертикальные плоскости (для деревьев): центр основания (x,y,z). */
    public void crossPlanes(float x, float y, float z, float width, float height, float rot) {
        for (int k = 0; k < 2; k++) {
            float a = rot + k * (float) (Math.PI / 2);
            float dx = (float) Math.cos(a) * width / 2, dz = (float) Math.sin(a) * width / 2;
            quad(x - dx, y, z - dz, x + dx, y, z + dz, x + dx, y + height, z + dz, x - dx, y + height, z - dz,
                    0, 0, 1, 1, 1, 1, 1);
        }
    }
}
