package ru.dvor.horror;

/**
 * Персонажи из примитивов: голова — эллипсоид с текстурой волос + «маска» с настоящим фото лица спереди
 * и пластина с кудрями сзади. Модель смотрит вдоль -Z (поворот rotateY(yaw)).
 */
final class Models {
    private final Renderer r;
    private final float[] root = new float[16], lm = new float[16], m = new float[16];

    Models(Renderer r) {
        this.r = r;
    }

    /** Сустав: lm = root * T(p) * Rx(ax) * Rz(az). Конечность свисает вниз по -Y. */
    private void limb(float px, float py, float pz, float ax, float az) {
        M4.copy(lm, root);
        M4.translate(lm, px, py, pz);
        M4.rotateX(lm, ax);
        M4.rotateZ(lm, az);
    }

    private void box(String tex, float[] base, float ox, float oy, float oz, float sx, float sy, float sz,
                     float cr, float cg, float cb, float emis) {
        M4.copy(m, base);
        M4.translate(m, ox, oy, oz);
        M4.scale(m, sx, sy, sz);
        r.cube(tex, m, cr, cg, cb, emis);
    }

    private void ball(String tex, float ox, float oy, float oz, float rx, float ry, float rz, float emis) {
        M4.copy(m, root);
        M4.translate(m, ox, oy, oz);
        M4.scale(m, rx, ry, rz);
        r.sphere(tex, m, 1f, 1f, 1f, emis);
    }

    private void facePlate(String tex, float ox, float oy, float oz, float size, boolean front, float emis) {
        M4.copy(m, root);
        M4.translate(m, ox, oy, oz);
        if (front) M4.rotateY(m, (float) Math.PI);
        M4.scale(m, size, size, 1f);
        r.plate(tex, m, emis);
    }

    // ------------------------------------------------------------------ герой

    void drawHero(Game g) {
        Player p = g.player;
        if (g.state == Game.CAUGHT) {
            // скример: камера прыгает к лицу врага и оказывается «внутри» героя — его не рисуем
            float cx = g.camPos[0] - p.x, cz = g.camPos[2] - p.z;
            if (cx * cx + cz * cz < 2.6f) return;
        }
        float mv = Math.min(1f, p.speed / 3f);
        float sw = (float) Math.sin(p.walkPhase) * (0.55f + (p.sprinting ? 0.35f : 0f)) * mv;
        float bob = Math.abs((float) Math.cos(p.walkPhase)) * 0.04f * mv;
        M4.identity(root);
        M4.translate(root, p.x, bob, p.z);
        M4.rotateY(root, p.yaw);
        if (p.sprinting) M4.rotateX(root, -0.1f);

        final float e = 0.16f;
        // ноги и ботинки
        for (int s = -1; s <= 1; s += 2) {
            limb(0.12f * s, 0.85f, 0, s < 0 ? sw : -sw, 0);
            box("white", lm, 0, -0.42f, 0, 0.17f, 0.84f, 0.2f, 0.06f, 0.06f, 0.08f, e);
            box("white", lm, 0, -0.8f, -0.06f, 0.2f, 0.1f, 0.34f, 0.03f, 0.03f, 0.03f, e);
        }
        // куртка
        box("white", root, 0, 1.15f, 0, 0.5f, 0.62f, 0.28f, 0.08f, 0.09f, 0.13f, e);
        box("white", root, 0, 1.12f, -0.145f, 0.2f, 0.58f, 0.01f, 0.1f, 0.1f, 0.12f, e);
        // руки: левая качается, правая вытянута с фонариком
        limb(-0.33f, 1.4f, 0, -sw * 0.9f, 0.05f);
        box("white", lm, 0, -0.3f, 0, 0.13f, 0.6f, 0.15f, 0.08f, 0.09f, 0.13f, e);
        box("white", lm, 0, -0.64f, 0, 0.1f, 0.1f, 0.1f, 0.8f, 0.62f, 0.5f, e);
        limb(0.33f, 1.4f, 0, 1.25f + 0.03f * (float) Math.sin(r.time * 2f), -0.05f);
        box("white", lm, 0, -0.3f, 0, 0.13f, 0.6f, 0.15f, 0.08f, 0.09f, 0.13f, e);
        box("white", lm, 0, -0.64f, 0, 0.1f, 0.1f, 0.1f, 0.8f, 0.62f, 0.5f, e);
        box("white", lm, 0, -0.76f, 0, 0.07f, 0.22f, 0.07f, 0.25f, 0.25f, 0.28f, 0.1f);
        box("white", lm, 0, -0.88f, 0, 0.06f, 0.02f, 0.06f, 1f, 0.95f, 0.8f, g.flashOn ? 1.6f : 0.1f);
        // голова: кудри + лицо с фото (скрываем, если камера почти внутри)
        if (g.camT > 0.3f) {
            ball("hero_hair", 0, 1.68f, 0, 0.28f, 0.3f, 0.28f, 0.22f);
            facePlate("hero_face", 0, 1.66f, -0.285f, 0.56f, true, 0.3f);
            facePlate("hero_back", 0, 1.68f, 0.285f, 0.58f, false, 0.2f);
        }
    }

    // ------------------------------------------------------------------ враг

    void drawEnemy(Game g) {
        Enemy en = g.enemy;
        float t = r.time;
        float lr = en.legRaise;
        float sw = en.moving ? (float) Math.sin(en.phase) * 0.8f : 0f;
        float bob = en.moving ? Math.abs((float) Math.cos(en.phase)) * 0.05f : (float) Math.sin(t * 1.5f) * 0.01f;
        M4.identity(root);
        M4.translate(root, en.x, bob - lr * 0.05f, en.z);
        M4.rotateY(root, en.yaw);

        final float e = 0.1f;
        // ноги: правая поднимается в сторону в позе «стойка на одной ноге»
        limb(-0.16f, 0.9f, 0, sw, 0);
        box("white", lm, 0, -0.45f, 0, 0.2f, 0.9f, 0.24f, 0.05f, 0.05f, 0.06f, e);
        box("white", lm, 0, -0.86f, -0.07f, 0.24f, 0.12f, 0.38f, 0.02f, 0.02f, 0.02f, e);
        limb(0.16f, 0.9f, 0, -sw * (1f - lr), lr * 0.95f);
        box("white", lm, 0, -0.45f, 0, 0.2f, 0.9f, 0.24f, 0.05f, 0.05f, 0.06f, e);
        box("white", lm, 0, -0.86f, -0.07f, 0.24f, 0.12f, 0.38f, 0.02f, 0.02f, 0.02f, e);
        // пуховик
        box("puffer", root, 0, 1.2f, 0, 0.72f, 0.8f, 0.42f, 1f, 1f, 1f, e);
        box("puffer", root, 0, 0.84f, 0, 0.76f, 0.14f, 0.46f, 1f, 1f, 1f, e);
        // руки расставлены в стороны; в погоне размахивает
        float flail = en.state == Enemy.CHASE ? (float) Math.sin(en.phase * 1.1f) * 0.25f : (float) Math.sin(t * 1.3f) * 0.07f;
        limb(-0.43f, 1.5f, 0, 0, -(1.45f + flail));
        box("puffer", lm, 0, -0.46f, 0, 0.3f, 0.96f, 0.32f, 1f, 1f, 1f, e);
        box("white", lm, 0, -1.0f, 0, 0.12f, 0.14f, 0.12f, 0.75f, 0.58f, 0.48f, e);
        limb(0.43f, 1.5f, 0, 0, 1.45f - flail);
        box("puffer", lm, 0, -0.46f, 0, 0.3f, 0.96f, 0.32f, 1f, 1f, 1f, e);
        box("white", lm, 0, -1.0f, 0, 0.12f, 0.14f, 0.12f, 0.75f, 0.58f, 0.48f, e);
        // сумка через плечо
        M4.copy(m, root);
        M4.translate(m, 0f, 1.2f, -0.215f);
        M4.rotateZ(m, 0.75f);
        M4.scale(m, 0.07f, 1.05f, 0.03f);
        r.cube("white", m, 0.07f, 0.07f, 0.08f, e);
        box("white", root, 0.34f, 0.9f, -0.24f, 0.3f, 0.26f, 0.14f, 0.08f, 0.08f, 0.1f, e);
        // голова: тёмные кудри + бледное лицо, слегка светящееся в темноте
        ball("enemy_hair", 0, 1.84f, 0, 0.3f, 0.32f, 0.3f, 0.15f);
        facePlate("enemy_face", 0, 1.82f, -0.305f, 0.64f, true, 0.6f);
        facePlate("enemy_back", 0, 1.84f, 0.305f, 0.66f, false, 0.15f);
    }
}
