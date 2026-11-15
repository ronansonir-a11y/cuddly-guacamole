package ru.dvor.horror;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Строит статическую геометрию двора (по батчам-текстурам) и регистрирует коллайдеры в Level.
 * Имена батчей: sand, grass, facade_a/b/c, fence, tree — текстуры; white — цветная геометрия;
 * emit — светящиеся детали (лампы).
 */
public final class LevelBuilder {
    final Level L;
    final Map<String, MeshBuilder> out = new LinkedHashMap<>();

    private LevelBuilder(Level level) {
        this.L = level;
    }

    MeshBuilder mb(String tex) {
        MeshBuilder b = out.get(tex);
        if (b == null) {
            b = new MeshBuilder();
            out.put(tex, b);
        }
        return b;
    }

    public static Map<String, MeshBuilder> build(Level level) {
        LevelBuilder b = new LevelBuilder(level);
        b.ground();
        b.buildings();
        b.fences();
        b.entrances();
        LevelProps.addAll(b);
        b.trees();
        return b.out;
    }

    static float hash(float x, float z) {
        float s = (float) Math.sin(x * 12.9898 + z * 78.233) * 43758.5453f;
        return s - (float) Math.floor(s);
    }

    static boolean isSand(float x, float z) {
        if (Math.abs(x) > Level.HX - 1.2f || Math.abs(z) > Level.HZ - 1.2f) return false;
        float h = hash(x, z) - 0.5f;
        float a = (x - 12) / 6f, b = (z + 6) / 4.5f;
        if (a * a + b * b < 1 + h * 0.6f) return false;
        a = (x + 16) / 5f;
        b = (z - 10) / 4f;
        return !(a * a + b * b < 1 + h * 0.6f);
    }

    private void ground() {
        MeshBuilder sand = mb("sand"), grass = mb("grass");
        for (int ix = -22; ix < 22; ix++) {
            for (int iz = -18; iz < 18; iz++) {
                float x0 = ix * 2, z0 = iz * 2;
                boolean s = isSand(x0 + 1, z0 + 1);
                (s ? sand : grass).floor(x0, z0, x0 + 2, z0 + 2, 0f, s ? 6f : 4f, 1, 1, 1);
            }
        }
    }

    private void buildings() {
        float h = 15f;
        mb("facade_a").wall(-32, -26, 32, -26, 0, h, 9, 9, 1, 1, 1);
        mb("facade_c").wall(32, 26, -32, 26, 0, h, 9, 9, 1, 1, 1);
        mb("facade_b").wall(32, -26, 32, 26, 0, h, 16, 8, 1, 1, 1);
        mb("facade_c").wall(-32, 26, -32, -26, 0, h, 9, 9, 1, 1, 1);
        // оранжевый цоколь, как у настоящих домов
        MeshBuilder w = mb("white");
        float[] c = {0.5f, 0.22f, 0.1f};
        w.wall(-32, -25.92f, 32, -25.92f, 0, 0.9f, 0, 0, c[0], c[1], c[2]);
        w.wall(32, 25.92f, -32, 25.92f, 0, 0.9f, 0, 0, c[0], c[1], c[2]);
        w.wall(31.92f, -26, 31.92f, 26, 0, 0.9f, 0, 0, c[0], c[1], c[2]);
        w.wall(-31.92f, 26, -31.92f, -26, 0, 0.9f, 0, 0, c[0], c[1], c[2]);
        // тела домов (стены, в которые упирается мир)
        L.addBox(-45, -40, 45, Level.FACADE_Z, true);
        L.addBox(-45, 26, 45, 40, true);
        L.addBox(32, -40, 45, 40, true);
        L.addBox(-45, -40, -32, 40, true);
    }

    private void fenceLine(float x0, float z0, float x1, float z1, float ox, float oz) {
        mb("fence").wall(x0, z0, x1, z1, 0, 2.4f, 2f, 2f, 1, 1, 1);
        L.addBox(Math.min(x0, x1) - 0.2f, Math.min(z0, z1) - 0.2f, Math.max(x0, x1) + 0.2f, Math.max(z0, z1) + 0.2f, false);
        float dx = x1 - x0, dz = z1 - z0, len = (float) Math.sqrt(dx * dx + dz * dz);
        int n = Math.max(1, Math.round(len / 3f));
        MeshBuilder w = mb("white");
        float[] m = new float[16];
        for (int i = 0; i <= n; i++) {
            float x = x0 + dx * i / n, z = z0 + dz * i / n;
            w.identity();
            w.box(x, 1.45f, z, 0.12f, 2.9f, 0.12f, 0.1f, 0.22f, 0.13f, 0);
            M4.identity(m);
            M4.translate(m, x, 2.8f, z);
            if (Math.abs(dz) < 1e-3f) M4.rotateX(m, oz > 0 ? 0.7f : -0.7f);
            else M4.rotateZ(m, ox > 0 ? -0.7f : 0.7f);
            w.transform(m);
            w.box(0, 0.35f, 0, 0.06f, 0.7f, 0.06f, 0.1f, 0.22f, 0.13f, 0);
            w.identity();
        }
    }

    private void fences() {
        float hx = Level.HX, hz = Level.HZ, g = Level.GATE_HW;
        fenceLine(-hx, -hz, -g, -hz, 0, -1);
        fenceLine(g, -hz, hx, -hz, 0, -1);
        fenceLine(hx, -hz, hx, hz, 1, 0);
        fenceLine(hx, hz, -hx, hz, 0, 1);
        fenceLine(-hx, hz, -hx, -hz, -1, 0);
        fenceLine(-g, -hz, -g, Level.FACADE_Z + 0.1f, -1, 0);
        fenceLine(g, -hz, g, Level.FACADE_Z + 0.1f, 1, 0);
        L.gateBox = L.addBox(-g, -hz - 0.2f, g, -hz + 0.2f, false);
        MeshBuilder w = mb("white");
        w.box(-g, 1.6f, -hz, 0.22f, 3.2f, 0.22f, 0.2f, 0.2f, 0.22f, 0);
        w.box(g, 1.6f, -hz, 0.22f, 3.2f, 0.22f, 0.2f, 0.2f, 0.22f, 0);
    }

    private void door(float x, boolean main) {
        MeshBuilder w = mb("white");
        float z = Level.FACADE_Z;
        w.box(x, 1.15f, z + 0.09f, 1.5f, 2.3f, 0.18f, 0.16f, 0.11f, 0.09f, 0);
        w.box(x, 1.95f, z + 0.19f, 0.5f, 0.45f, 0.02f, 0.08f, 0.12f, 0.16f, 0);
        w.box(x, 0.1f, z + 0.65f, 2.6f, 0.2f, 1.1f, 0.42f, 0.42f, 0.44f, 0);
        w.box(x, 2.9f, z + 0.75f, 3.2f, 0.14f, 1.5f, 0.38f, 0.38f, 0.4f, 0);
        w.box(x + 1.05f, 1.4f, z + 0.15f, 0.18f, 0.28f, 0.05f, 0.3f, 0.3f, 0.33f, 0);
        mb("emit").box(x, 2.78f, z + 0.5f, 0.32f, 0.08f, 0.22f, main ? 1f : 0.55f, main ? 0.85f : 0.5f, main ? 0.5f : 0.3f, 0);
    }

    private void entrances() {
        door(0, true);
        door(-14, false);
        door(14, false);
        L.addBox(-Level.GATE_HW - 0.3f, Level.FACADE_Z - 6, Level.GATE_HW + 0.3f, Level.FACADE_Z, true);
    }

    private void tree(float x, float z, float h, boolean solid, float rot) {
        mb("tree").crossPlanes(x, 0, z, h * 0.62f, h, rot);
        if (solid) L.addCircle(x, z, 0.28f);
    }

    private void trees() {
        float[][] inside = {{-3, -14}, {10, -3}, {-18, -1}, {8, 13}, {-12, 11}, {23, 5}, {-22, 16}, {-1.5f, -8}, {-6, 3}};
        for (int i = 0; i < inside.length; i++)
            tree(inside[i][0], inside[i][1], 8.5f + hash(inside[i][0], inside[i][1]) * 3.5f, true, i * 0.9f);
        // деревья за забором (по краям двора)
        for (int k = -3; k <= 3; k++) {
            float z = k * 6f + 1f;
            tree(28.6f, z, 9 + hash(28, z) * 3, false, k * 0.7f);
            tree(-28.6f, z + 2f, 9 + hash(-28, z) * 3, false, k * 0.5f);
        }
        for (int k = -4; k <= 4; k++) {
            if (Math.abs(k * 6f) < 4f) continue;
            tree(k * 6f, -23.5f, 9 + hash(k, 1) * 3, false, k * 0.4f);
            tree(k * 6f + 2f, 23.5f, 9 + hash(k, 2) * 3, false, k * 0.6f);
        }
    }
}
