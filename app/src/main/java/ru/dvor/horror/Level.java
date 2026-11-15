package ru.dvor.horror;

import java.util.ArrayList;

/**
 * Данные уровня и запросы столкновений (всё в плоскости XZ).
 * Оси: x — на восток, z — на юг, y — вверх. Север (z<0) — дом №4 с подъездом.
 */
public final class Level {
    /** Половина размеров двора внутри забора. */
    public static final float HX = 26f, HZ = 20f;
    /** Половина ширины ворот в северном заборе (x=0, z=-HZ). */
    public static final float GATE_HW = 2.2f;
    /** Плоскость фасада северного дома и дверь подъезда. */
    public static final float FACADE_Z = -26f;
    public static final float DOOR_Z = -25.2f;

    public static final class Box {
        public final float x0, z0, x1, z1;
        public final boolean opaque;
        public boolean enabled = true;

        Box(float x0, float z0, float x1, float z1, boolean opaque) {
            this.x0 = Math.min(x0, x1);
            this.x1 = Math.max(x0, x1);
            this.z0 = Math.min(z0, z1);
            this.z1 = Math.max(z0, z1);
            this.opaque = opaque;
        }
    }

    public static final class Circ {
        public final float x, z, r;

        Circ(float x, float z, float r) {
            this.x = x;
            this.z = z;
            this.r = r;
        }
    }

    public final ArrayList<Box> boxes = new ArrayList<>();
    public final ArrayList<Circ> circles = new ArrayList<>();
    public Box gateBox;

    /** Возможные места ключей (x, z). В каждой игре выбирается несколько. */
    public static final float[][] KEY_SPOTS = {
            {-20, -14}, {-19, 8}, {-5, 12}, {10, 16}, {21, 12}, {21, -9},
            {11, -5}, {-11, -6}, {1, 1}, {-24, -3}, {24, 4}, {6, -17}
    };

    /** Точки патруля врага. */
    public static final float[][] PATROL = {
            {-20, -14}, {-20, 0}, {-17, 12}, {-8, 14}, {0, 6}, {8, 14}, {20, 14},
            {22, 0}, {20, -14}, {8, -15}, {-5, -14}, {10, 0}, {-10, 2}, {0, -4}
    };

    /** Фонари: x, y, z, r, g, b (цвет включает интенсивность). Индекс 0 мигает. */
    public static final float[][] LAMPS = {
            {-10f, 5.6f, -14f, 1.10f, 0.62f, 0.26f},
            {18f, 5.6f, 15f, 0.95f, 0.58f, 0.25f},
    };

    public static final float SPAWN_X = 0f, SPAWN_Z = 16f;

    public Box addBox(float x0, float z0, float x1, float z1, boolean opaque) {
        Box b = new Box(x0, z0, x1, z1, opaque);
        boxes.add(b);
        return b;
    }

    public void addCircle(float x, float z, float r) {
        circles.add(new Circ(x, z, r));
    }

    /** Выталкивает круг радиуса r из препятствий. p = {x, z}. Возвращает true, если позиция изменилась. */
    public boolean push(float[] p, float r) {
        boolean moved = false;
        for (int it = 0; it < 3; it++) {
            boolean any = false;
            for (int i = 0; i < boxes.size(); i++) {
                Box b = boxes.get(i);
                if (!b.enabled) continue;
                float cx = Math.max(b.x0, Math.min(p[0], b.x1)), cz = Math.max(b.z0, Math.min(p[1], b.z1));
                float dx = p[0] - cx, dz = p[1] - cz, d2 = dx * dx + dz * dz;
                if (d2 >= r * r) continue;
                if (d2 > 1e-8f) {
                    float d = (float) Math.sqrt(d2), k = (r - d) / d;
                    p[0] += dx * k;
                    p[1] += dz * k;
                } else {
                    float l = p[0] - b.x0, rr = b.x1 - p[0], t = p[1] - b.z0, bt = b.z1 - p[1];
                    float m = Math.min(Math.min(l, rr), Math.min(t, bt));
                    if (m == l) p[0] = b.x0 - r;
                    else if (m == rr) p[0] = b.x1 + r;
                    else if (m == t) p[1] = b.z0 - r;
                    else p[1] = b.z1 + r;
                }
                any = true;
            }
            for (int i = 0; i < circles.size(); i++) {
                Circ c = circles.get(i);
                float dx = p[0] - c.x, dz = p[1] - c.z, rr = r + c.r, d2 = dx * dx + dz * dz;
                if (d2 >= rr * rr) continue;
                float d = (float) Math.sqrt(d2);
                if (d < 1e-5f) {
                    p[0] += rr;
                } else {
                    float k = (rr - d) / d;
                    p[0] += dx * k;
                    p[1] += dz * k;
                }
                any = true;
            }
            if (!any) break;
            moved = true;
        }
        return moved;
    }

    /** Пересекается ли круг радиуса r с препятствием. */
    public boolean blocked(float x, float z, float r) {
        for (int i = 0; i < boxes.size(); i++) {
            Box b = boxes.get(i);
            if (!b.enabled) continue;
            float cx = Math.max(b.x0, Math.min(x, b.x1)), cz = Math.max(b.z0, Math.min(z, b.z1));
            float dx = x - cx, dz = z - cz;
            if (dx * dx + dz * dz < r * r) return true;
        }
        for (int i = 0; i < circles.size(); i++) {
            Circ c = circles.get(i);
            float dx = x - c.x, dz = z - c.z, rr = r + c.r;
            if (dx * dx + dz * dz < rr * rr) return true;
        }
        return false;
    }

    /** Свободна ли прямая между точками для круга радиуса r. */
    public boolean rayClear(float x0, float z0, float x1, float z1, float r) {
        float dx = x1 - x0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        int steps = Math.max(1, (int) (len / 0.3f));
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            if (blocked(x0 + dx * t, z0 + dz * t, r)) return false;
        }
        return true;
    }

    /** Прямая видимость: пересекает ли отрезок непрозрачные препятствия. */
    public boolean los(float x0, float z0, float x1, float z1) {
        for (int i = 0; i < boxes.size(); i++) {
            Box b = boxes.get(i);
            if (b.opaque && b.enabled && segHitsBox(x0, z0, x1, z1, b)) return false;
        }
        return true;
    }

    private static boolean segHitsBox(float x0, float z0, float x1, float z1, Box b) {
        float dx = x1 - x0, dz = z1 - z0, t0 = 0, t1 = 1;
        if (Math.abs(dx) < 1e-9f) {
            if (x0 < b.x0 || x0 > b.x1) return false;
        } else {
            float ta = (b.x0 - x0) / dx, tb = (b.x1 - x0) / dx;
            if (ta > tb) {
                float t = ta;
                ta = tb;
                tb = t;
            }
            t0 = Math.max(t0, ta);
            t1 = Math.min(t1, tb);
            if (t0 > t1) return false;
        }
        if (Math.abs(dz) < 1e-9f) {
            if (z0 < b.z0 || z0 > b.z1) return false;
        } else {
            float ta = (b.z0 - z0) / dz, tb = (b.z1 - z0) / dz;
            if (ta > tb) {
                float t = ta;
                ta = tb;
                tb = t;
            }
            t0 = Math.max(t0, ta);
            t1 = Math.min(t1, tb);
            if (t0 > t1) return false;
        }
        return true;
    }
}
