package ru.dvor.horror;

import java.util.Map;
import java.util.Random;

/** Состояние и правила игры. update() вызывается из GL-потока; поля для HUD — volatile. */
public final class Game {
    public static final int KEYS_TOTAL = 5;
    public static final int MENU = 0, PLAYING = 1, CAUGHT = 2, WON = 3;
    private static final float LOOK_SENS = 0.0042f;

    public interface Listener {
        void onKey(int have, int total);

        void onGateOpen();

        void onCaught();

        void onWin(float seconds);
    }

    private static final class NoSfx implements Sfx {
        public void step(boolean run) { }
        public void keyPickup() { }
        public void gateUnlock() { }
        public void scare() { }
        public void creak() { }
        public void ambient(float fear, float near) { }
        public void pause(boolean paused) { }
    }

    public final Level level = new Level();
    public final Map<String, MeshBuilder> meshes;
    public final Nav nav;
    public final Player player = new Player();
    public final Enemy enemy;
    public final Input input = new Input();
    public final Random rnd;
    public volatile Sfx sfx = new NoSfx();
    public volatile Listener listener;

    public volatile int state = MENU;
    public volatile boolean paused;
    public final float[] keyX = new float[KEYS_TOTAL], keyZ = new float[KEYS_TOTAL];
    public final boolean[] keyTaken = new boolean[KEYS_TOTAL];
    public volatile int keysHave;
    public boolean gateOpen;
    public float gateAnim;
    public volatile float time, fear, stamina = 1f;
    public volatile String hint = "";
    public volatile float hintT;
    public volatile boolean flashOn = true;

    // камера и свет (читает рендерер)
    public float camYaw, camPitch = 0.12f, camT = 1f, fov = 1.08f;
    public final float[] camPos = new float[3], camFwd = {0, 0, -1};
    public final float[] flashPos = new float[3], flashDir = {0, 0, -1};
    public float flashPower = 1f, lampFlicker = 1f, scareT;

    private final float[] look = new float[2];
    private float flickerT, creakT = 12f, hintCd, noiseT;

    public Game(long seed) {
        rnd = new Random(seed);
        meshes = LevelBuilder.build(level);
        nav = new Nav(level, 0.5f);
        enemy = new Enemy(rnd);
        start();
        state = MENU;
    }

    /** Новая партия. Вызывать из GL-потока. */
    public void start() {
        float[][] spots = Level.KEY_SPOTS;
        int[] order = new int[spots.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        for (int i = order.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1), t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        int n = 0;
        for (float spacing = 9f; n < KEYS_TOTAL && spacing >= 0f; spacing -= 3f) {
            n = 0;
            for (int k = 0; k < order.length && n < KEYS_TOTAL; k++) {
                float x = spots[order[k]][0], z = spots[order[k]][1];
                if (Math.hypot(x - Level.SPAWN_X, z - Level.SPAWN_Z) < 9) continue;
                boolean ok = true;
                for (int i = 0; i < n; i++) if (Math.hypot(x - keyX[i], z - keyZ[i]) < spacing) ok = false;
                if (!ok) continue;
                keyX[n] = x;
                keyZ[n] = z;
                n++;
            }
        }
        for (int i = 0; i < KEYS_TOTAL; i++) keyTaken[i] = false;
        keysHave = 0;
        gateOpen = false;
        gateAnim = 0;
        level.gateBox.enabled = true;
        time = fear = scareT = 0;
        stamina = 1f;
        hint = "";
        hintT = hintCd = 0;
        flickerT = 0;
        creakT = 10f + rnd.nextFloat() * 10f;
        player.reset(Level.SPAWN_X, Level.SPAWN_Z);
        flashOn = true;
        // враг стартует в случайной точке патруля, не ближе 22 м от героя
        float[][] pts = Level.PATROL;
        int pick = -1, seen = 0;
        for (int i = 0; i < pts.length; i++) {
            if (Math.hypot(pts[i][0] - Level.SPAWN_X, pts[i][1] - Level.SPAWN_Z) < 22) continue;
            seen++;
            if (rnd.nextInt(seen) == 0) pick = i;
        }
        if (pick < 0) pick = 0;
        enemy.reset(pts[pick][0], pts[pick][1], player.x, player.z);
        enemy.chaseSpeed = 3.3f;
        camYaw = 0;
        camPitch = 0.12f;
        camT = 1f;
        input.reset();
        updateCamera(0.016f);
        state = PLAYING;
    }

    private void showHint(String text, float seconds) {
        hint = text;
        hintT = seconds;
    }

    public void update(float dt) {
        if (state == MENU || paused) return;
        time += (state == PLAYING) ? dt : 0f;
        hintT = Math.max(0f, hintT - dt);

        if (state == PLAYING) {
            input.takeLook(look);
            camYaw -= look[0] * LOOK_SENS;
            camPitch = Math.max(-0.55f, Math.min(0.85f, camPitch - look[1] * LOOK_SENS));
            if (input.takeFlash()) player.flashOn = !player.flashOn;
            flashOn = player.flashOn;
            player.update(this, input, camYaw, dt);
            stamina = player.stamina;

            enemy.chaseSpeed = 3.3f + 0.12f * keysHave + (gateOpen ? 0.25f : 0f);
            enemy.update(this, dt);

            collectKeys();
            gateLogic(dt);
            hints(dt);
            fearAndAtmosphere(dt);

            if (enemy.touching(player)) {
                state = CAUGHT;
                scareT = 0;
                sfx.scare();
                Listener l = listener;
                if (l != null) l.onCaught();
            } else if (gateOpen && player.z < Level.FACADE_Z + 1.4f && Math.abs(player.x) < Level.GATE_HW) {
                state = WON;
                Listener l = listener;
                if (l != null) l.onWin(time);
            }
        } else if (state == CAUGHT) {
            scareT += dt;
            enemy.lookAt(player.x, player.z, dt);
            sfx.ambient(1f, 1f);
        } else if (state == WON) {
            sfx.ambient(0f, 0f);
        }
        updateCamera(dt);
    }

    private void collectKeys() {
        for (int i = 0; i < KEYS_TOTAL; i++) {
            if (keyTaken[i]) continue;
            if (Math.hypot(player.x - keyX[i], player.z - keyZ[i]) < 1.0) {
                keyTaken[i] = true;
                keysHave++;
                sfx.keyPickup();
                enemy.alert(player.x, player.z);
                Listener l = listener;
                if (l != null) l.onKey(keysHave, KEYS_TOTAL);
                if (keysHave >= KEYS_TOTAL) {
                    gateOpen = true;
                    sfx.gateUnlock();
                    showHint("Ворота открыты! Беги в подъезд!", 6f);
                    if (l != null) l.onGateOpen();
                } else {
                    showHint("Ключ найден: " + keysHave + " из " + KEYS_TOTAL, 3f);
                }
                flickerT = 1.2f;
            }
        }
    }

    private void gateLogic(float dt) {
        if (gateOpen) {
            gateAnim = Math.min(1f, gateAnim + dt / 1.6f);
            if (gateAnim > 0.6f) level.gateBox.enabled = false;
        }
    }

    private void hints(float dt) {
        hintCd -= dt;
        if (!gateOpen && hintCd <= 0 && Math.hypot(player.x, player.z + Level.HZ) < 4.5) {
            showHint("Ворота заперты. Найди ключи: " + keysHave + " из " + KEYS_TOTAL, 3.5f);
            hintCd = 6f;
        }
    }

    private void fearAndAtmosphere(float dt) {
        float d = enemy.distTo(player);
        float f = Math.max(0f, Math.min(1f, (26f - d) / 22f));
        f *= f;
        if (enemy.state == Enemy.CHASE) f = Math.max(f, 0.5f);
        if (enemy.sees) f = Math.max(f, 0.25f);
        fear += (f - fear) * Math.min(1f, dt * 2.2f);
        sfx.ambient(fear, Math.max(0f, 1f - d / 30f));

        creakT -= dt;
        if (creakT <= 0) {
            sfx.creak();
            creakT = 14f + rnd.nextFloat() * 18f;
        }
        if (flickerT <= 0 && fear > 0.35f && rnd.nextFloat() < dt * fear * 0.5f) flickerT = 0.25f + rnd.nextFloat() * 0.5f;
        if (flickerT > 0) {
            flickerT -= dt;
            flashPower = rnd.nextFloat() < 0.5f ? 1f : 0.12f;
        } else {
            flashPower = 1f;
        }
        noiseT += dt;
        lampFlicker = (float) (0.8 + 0.2 * Math.sin(noiseT * 31.0) * Math.sin(noiseT * 7.3));
        if (rnd.nextFloat() < dt * 0.6f) lampFlicker *= 0.25f;
    }

    /** Камера от третьего лица через плечо; в CAUGHT — бросок к лицу врага. */
    private void updateCamera(float dt) {
        float cp = (float) Math.cos(camPitch), sp = (float) Math.sin(camPitch);
        float sy = (float) Math.sin(camYaw), cy = (float) Math.cos(camYaw);
        float fx = -sy * cp, fy = sp, fz = -cy * cp;
        float rx = cy, rz = -sy;
        float hx = player.x, hy = 1.55f, hz = player.z, dist = 3.1f;
        float t = 1f;
        for (int i = 1; i <= 14; i++) {
            float s = i / 14f;
            float qx = hx - fx * dist * s, qz = hz - fz * dist * s;
            if (level.blocked(qx, qz, 0.3f) && hy - fy * dist * s < 3.4f) {
                t = Math.max(0.15f, (i - 1) / 14f);
                break;
            }
        }
        camT = t;
        float px = hx - fx * dist * t + rx * 0.5f * t;
        float py = Math.max(0.3f, hy - fy * dist * t);
        float pz = hz - fz * dist * t + rz * 0.5f * t;
        float fv = 1.08f + fear * 0.1f + (player.sprinting ? 0.07f : 0f);
        float shake = fear * fear * 0.03f;
        px += (float) Math.sin(time * 41.0) * shake;
        py += (float) Math.sin(time * 53.0 + 1.0) * shake;

        if (state == CAUGHT) {
            float e = Math.min(1f, scareT / 0.22f);
            e = e * e * (3f - 2f * e);
            float ex = enemy.x, ez = enemy.z;
            float dx = player.x - ex, dz = player.z - ez, dl = (float) Math.max(0.01, Math.hypot(dx, dz));
            float tx = ex + dx / dl * 0.95f, tz = ez + dz / dl * 0.95f;
            px += (tx - px) * e;
            py += (1.75f - py) * e;
            pz += (tz - pz) * e;
            float lx = ex - px, ly = 1.78f - py, lz = ez - pz;
            float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
            fx += (lx / ll - fx) * e;
            fy += (ly / ll - fy) * e;
            fz += (lz / ll - fz) * e;
            float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
            fx /= fl;
            fy /= fl;
            fz /= fl;
            fv = 1.08f + 0.5f * e + (float) Math.sin(scareT * 60.0) * 0.03f;
            px += (float) Math.sin(scareT * 90.0) * 0.03f * e;
        }
        camPos[0] = px;
        camPos[1] = py;
        camPos[2] = pz;
        camFwd[0] = fx;
        camFwd[1] = fy;
        camFwd[2] = fz;
        fov = fv;

        float by = player.yaw;
        float hx2 = player.x + (float) Math.cos(by) * 0.32f - (float) Math.sin(by) * 0.3f;
        float hz2 = player.z - (float) Math.sin(by) * 0.32f - (float) Math.cos(by) * 0.3f;
        float hy2 = 1.25f;
        float ax = px + fx * 14f, ay = py + fy * 14f, az = pz + fz * 14f;
        float dx = ax - hx2, dy = ay - hy2, dz = az - hz2;
        float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        flashPos[0] = hx2;
        flashPos[1] = hy2;
        flashPos[2] = hz2;
        flashDir[0] = dx / dl;
        flashDir[1] = dy / dl;
        flashDir[2] = dz / dl;
    }
}
