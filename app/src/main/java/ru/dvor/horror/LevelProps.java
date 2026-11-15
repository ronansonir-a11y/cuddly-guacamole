package ru.dvor.horror;

/** Мелкие объекты двора: баскетбольный щит, ворота, горка, качели, лавки, машины, фонари. */
final class LevelProps {
    private LevelProps() {
    }

    static void addAll(LevelBuilder b) {
        hoop(b, -7, -2);
        goal(b, -14, -9);
        playground(b, 15, 9);
        swings(b, 6, -12);
        bench(b, -20, 14, true);
        bench(b, 21, -3, false);
        bench(b, -3, 17.6f, true);
        sandbox(b, 22, -14);
        bin(b, -9, 5);
        bin(b, 12, -15);
        bin(b, -22, -5);
        bin(b, 4, 6);
        car(b, -14, 17.3f, true, 0, true);
        car(b, -8.8f, 17.3f, true, 1, true);
        car(b, 19, 17.3f, true, 2, true);
        car(b, -23.8f, 4, false, 3, true);
        car(b, -23.8f, 9.6f, false, 4, true);
        car(b, 29, -10, false, 5, false);
        car(b, 29, 0, false, 6, false);
        car(b, 29, 12, false, 7, false);
        car(b, -29, -8, false, 1, false);
        car(b, -29, 14, false, 0, false);
        lampPost(b, -10, -14);
        lampPost(b, 18, 15);
        booth(b, 3f, -6f);
        shed(b, 24.8f, -4f, 2.0f, 6.0f);
        hedge(b, -12f, 6.5f, 5f, 1f);
        hedge(b, 8f, 2f, 1f, 5f);
        hedge(b, -4f, -9f, 1f, 4f);
    }

    /** Трансформаторная будка: кирпичная коробка, непрозрачная (прячет героя от врага). */
    private static void booth(LevelBuilder b, float cx, float cz) {
        MeshBuilder w = b.mb("white");
        w.box(cx, 1.5f, cz, 4f, 3f, 3f, 0.42f, 0.2f, 0.15f, 0);
        w.box(cx, 3.08f, cz, 4.3f, 0.16f, 3.3f, 0.15f, 0.15f, 0.17f, 0);
        w.box(cx, 1.0f, cz + 1.52f, 0.9f, 2f, 0.05f, 0.1f, 0.1f, 0.12f, 0);
        b.L.addBox(cx - 2f, cz - 1.5f, cx + 2f, cz + 1.5f, true);
    }

    /** Гаражи-«пеналы» вдоль забора: длинная ржавая стена. */
    private static void shed(LevelBuilder b, float cx, float cz, float sx, float sz) {
        MeshBuilder w = b.mb("white");
        w.box(cx, 1.2f, cz, sx, 2.4f, sz, 0.3f, 0.2f, 0.14f, 0);
        w.box(cx, 2.45f, cz, sx + 0.2f, 0.1f, sz + 0.2f, 0.12f, 0.12f, 0.13f, 0);
        b.L.addBox(cx - sx / 2, cz - sz / 2, cx + sx / 2, cz + sz / 2, true);
    }

    /** Живая изгородь: низкая, но непрозрачная для обзора врага. */
    private static void hedge(LevelBuilder b, float cx, float cz, float sx, float sz) {
        b.mb("white").box(cx, 0.75f, cz, sx, 1.5f, sz, 0.05f, 0.14f, 0.06f, 0);
        b.L.addBox(cx - sx / 2, cz - sz / 2, cx + sx / 2, cz + sz / 2, true);
    }

    private static void hoop(LevelBuilder b, float x, float z) {
        MeshBuilder w = b.mb("white");
        w.cylinder(x, 0, z, 0.08f, 3.4f, 8, 0.12f, 0.12f, 0.14f, true);
        w.box(x + 0.35f, 3.1f, z, 0.7f, 0.08f, 0.08f, 0.12f, 0.12f, 0.14f, 0);
        w.box(x + 0.72f, 3.2f, z, 0.06f, 1.0f, 1.7f, 0.08f, 0.2f, 0.55f, 0);
        w.box(x + 0.76f, 3.05f, z, 0.03f, 0.45f, 0.8f, 0.85f, 0.85f, 0.9f, 0);
        w.ring(x + 1.0f, 2.85f, z, 0.2f, 0.25f, 14, 0.9f, 0.3f, 0.05f);
        b.L.addCircle(x, z, 0.15f);
    }

    private static void goal(LevelBuilder b, float x, float z) {
        MeshBuilder w = b.mb("white");
        float[] c = {0.6f, 0.52f, 0.3f};
        for (int s = -1; s <= 1; s += 2) {
            w.box(x, 1.05f, z + 1.5f * s, 0.11f, 2.1f, 0.11f, c[0], c[1], c[2], 0);
            w.box(x - 1f, 1.05f, z + 1.5f * s, 0.09f, 2.1f, 0.09f, c[0], c[1], c[2], 0);
            w.box(x - 0.5f, 2.1f, z + 1.5f * s, 1.0f, 0.08f, 0.08f, c[0], c[1], c[2], 0);
            b.L.addCircle(x, z + 1.5f * s, 0.12f);
            b.L.addCircle(x - 1f, z + 1.5f * s, 0.1f);
        }
        w.box(x, 2.1f, z, 0.11f, 0.11f, 3.1f, c[0], c[1], c[2], 0);
        w.box(x - 1f, 2.1f, z, 0.09f, 0.09f, 3.1f, c[0], c[1], c[2], 0);
    }

    private static void playground(LevelBuilder b, float cx, float cz) {
        MeshBuilder w = b.mb("white");
        w.box(cx - 0.8f, 1.2f, cz - 0.9f, 1.6f, 2.4f, 1.6f, 0.5f, 0.1f, 0.08f, 0);
        w.box(cx - 0.8f, 2.5f, cz - 0.9f, 2.0f, 0.14f, 2.0f, 0.65f, 0.5f, 0.08f, 0);
        w.box(cx + 1.2f, 0.9f, cz - 0.9f, 1.2f, 1.8f, 1.6f, 0.08f, 0.18f, 0.45f, 0);
        w.box(cx + 1.2f, 1.9f, cz - 0.9f, 1.5f, 0.12f, 1.9f, 0.65f, 0.5f, 0.08f, 0);
        float[] m = new float[16];
        M4.identity(m);
        M4.translate(m, cx + 1.2f, 0.85f, cz + 1.45f);
        M4.rotateX(m, 0.42f);
        w.transform(m);
        w.box(0, 0, 0, 0.7f, 0.06f, 3.5f, 0.7f, 0.7f, 0.75f, 0);
        w.box(-0.38f, 0.1f, 0, 0.05f, 0.2f, 3.5f, 0.5f, 0.1f, 0.08f, 0);
        w.box(0.38f, 0.1f, 0, 0.05f, 0.2f, 3.5f, 0.5f, 0.1f, 0.08f, 0);
        w.identity();
        b.L.addBox(cx - 1.7f, cz - 1.7f, cx + 1.85f, cz - 0.1f, true);
        b.L.addBox(cx + 0.8f, cz - 0.1f, cx + 1.6f, cz + 3.1f, false);
    }

    private static void swings(LevelBuilder b, float cx, float cz) {
        MeshBuilder w = b.mb("white");
        float[] m = new float[16];
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                M4.identity(m);
                M4.translate(m, cx + 1.6f * sx, 0, cz + 0.8f * sz);
                M4.rotateX(m, -0.32f * sz);
                w.transform(m);
                w.box(0, 1.265f, 0, 0.1f, 2.53f, 0.1f, 0.5f, 0.1f, 0.08f, 0);
                w.identity();
                b.L.addCircle(cx + 1.6f * sx, cz + 0.8f * sz, 0.2f);
            }
        }
        w.box(cx, 2.4f, cz, 3.4f, 0.1f, 0.1f, 0.5f, 0.1f, 0.08f, 0);
        for (int s = -1; s <= 1; s += 2) {
            float sxp = cx + 0.55f * s;
            w.box(sxp - 0.18f, 1.4f, cz, 0.02f, 1.9f, 0.02f, 0.4f, 0.4f, 0.42f, 0);
            w.box(sxp + 0.18f, 1.4f, cz, 0.02f, 1.9f, 0.02f, 0.4f, 0.4f, 0.42f, 0);
            w.box(sxp, 0.45f, cz, 0.45f, 0.04f, 0.2f, 0.05f, 0.05f, 0.06f, 0);
        }
    }

    private static void bench(LevelBuilder b, float x, float z, boolean alongX) {
        MeshBuilder w = b.mb("white");
        float[] m = new float[16];
        M4.identity(m);
        M4.translate(m, x, 0, z);
        if (!alongX) M4.rotateY(m, (float) (Math.PI / 2));
        w.transform(m);
        w.box(0, 0.45f, 0, 1.6f, 0.06f, 0.45f, 0.36f, 0.2f, 0.1f, 0);
        w.box(0, 0.78f, -0.2f, 1.6f, 0.4f, 0.05f, 0.36f, 0.2f, 0.1f, 0);
        w.box(-0.7f, 0.22f, 0, 0.06f, 0.45f, 0.42f, 0.08f, 0.08f, 0.1f, 0);
        w.box(0.7f, 0.22f, 0, 0.06f, 0.45f, 0.42f, 0.08f, 0.08f, 0.1f, 0);
        w.identity();
        if (alongX) b.L.addBox(x - 0.85f, z - 0.35f, x + 0.85f, z + 0.35f, false);
        else b.L.addBox(x - 0.35f, z - 0.85f, x + 0.35f, z + 0.85f, false);
    }

    private static void sandbox(LevelBuilder b, float x, float z) {
        MeshBuilder w = b.mb("white");
        float[] c = {0.3f, 0.18f, 0.08f};
        w.box(x, 0.15f, z - 1.5f, 3.2f, 0.3f, 0.16f, c[0], c[1], c[2], 0);
        w.box(x, 0.15f, z + 1.5f, 3.2f, 0.3f, 0.16f, c[0], c[1], c[2], 0);
        w.box(x - 1.5f, 0.15f, z, 0.16f, 0.3f, 3.0f, c[0], c[1], c[2], 0);
        w.box(x + 1.5f, 0.15f, z, 0.16f, 0.3f, 3.0f, c[0], c[1], c[2], 0);
    }

    private static void bin(LevelBuilder b, float x, float z) {
        b.mb("white").cylinder(x, 0, z, 0.28f, 0.7f, 10, 0.55f, 0.55f, 0.57f, true);
        b.L.addCircle(x, z, 0.3f);
    }

    private static final float[][] CAR_COLORS = {
            {0.7f, 0.7f, 0.74f}, {0.08f, 0.08f, 0.1f}, {0.4f, 0.07f, 0.07f}, {0.12f, 0.17f, 0.28f},
            {0.28f, 0.3f, 0.27f}, {0.5f, 0.46f, 0.36f}, {0.18f, 0.18f, 0.2f}, {0.55f, 0.55f, 0.58f}
    };

    private static void car(LevelBuilder b, float x, float z, boolean alongX, int color, boolean solid) {
        MeshBuilder w = b.mb("white");
        float[] c = CAR_COLORS[color % CAR_COLORS.length];
        float[] m = new float[16];
        M4.identity(m);
        M4.translate(m, x, 0, z);
        if (!alongX) M4.rotateY(m, (float) (Math.PI / 2));
        w.transform(m);
        w.box(0, 0.65f, 0, 4.3f, 0.7f, 1.8f, c[0], c[1], c[2], 0);
        w.box(-0.2f, 1.2f, 0, 2.3f, 0.55f, 1.6f, c[0] * 0.9f, c[1] * 0.9f, c[2] * 0.9f, 0);
        w.box(-0.2f, 1.2f, 0, 2.34f, 0.3f, 1.64f, 0.03f, 0.05f, 0.08f, 0);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float[] wm = new float[16];
                M4.copy(wm, m);
                M4.translate(wm, 1.35f * sx, 0.32f, 0.9f * sz);
                M4.rotateX(wm, (float) (Math.PI / 2));
                w.transform(wm);
                w.cylinder(0, -0.1f, 0, 0.32f, 0.2f, 10, 0.03f, 0.03f, 0.03f, true);
            }
        }
        w.identity();
        if (solid) {
            if (alongX) b.L.addBox(x - 2.15f, z - 0.95f, x + 2.15f, z + 0.95f, true);
            else b.L.addBox(x - 0.95f, z - 2.15f, x + 0.95f, z + 2.15f, true);
        }
    }

    private static void lampPost(LevelBuilder b, float x, float z) {
        b.mb("white").cylinder(x, 0, z, 0.07f, 5.6f, 8, 0.15f, 0.15f, 0.17f, true);
        b.mb("emit").box(x, 5.68f, z, 0.5f, 0.12f, 0.3f, 1f, 0.8f, 0.5f, 0);
        b.L.addCircle(x, z, 0.12f);
    }
}
