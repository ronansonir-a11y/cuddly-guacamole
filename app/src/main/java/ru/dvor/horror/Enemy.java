package ru.dvor.horror;

import java.util.Random;

/** «Кудрявый с сумкой»: патрулирует двор, замечает свет и топот, гонится по сетке (A*). */
public final class Enemy {
    public static final int PATROL = 0, STARE = 1, INVESTIGATE = 2, CHASE = 3, SEARCH = 4;
    public static final float R = 0.4f;

    public float x, z, yaw;
    public int state = PATROL;
    public boolean moving;
    public float phase;
    /** 0..1: поднятая в сторону нога (поза со стойки на одной ноге). */
    public float legRaise;
    public float chaseSpeed = 3.5f;
    public boolean sees;

    private final Random rnd;
    private final float[] path = new float[2 * Nav.W * Nav.H];
    private int pathLen, pathPos;
    private float repathT, timer, idleT, stareCd, loseT, chaseClock, restT;
    private float tx, tz, lastX, lastZ, goalX, goalZ;
    private final float[] tmp = new float[2];

    public Enemy(Random rnd) {
        this.rnd = rnd;
    }

    public void reset(float px, float pz, float playerX, float playerZ) {
        x = px;
        z = pz;
        yaw = (float) Math.atan2(-(playerX - px), -(playerZ - pz));
        state = PATROL;
        moving = false;
        legRaise = 0;
        sees = false;
        pathLen = pathPos = 0;
        repathT = timer = idleT = loseT = chaseClock = restT = 0;
        stareCd = 10f + rnd.nextFloat() * 8f;
        pickPatrol(playerX, playerZ);
    }

    public float distTo(Player p) {
        float dx = p.x - x, dz = p.z - z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    /** Достаточно близко, чтобы поймать героя. */
    public boolean touching(Player p) {
        return distTo(p) < 0.95f;
    }

    private void pickPatrol(float px, float pz) {
        float[][] pts = Level.PATROL;
        int best = -1;
        for (int tries = 0; tries < 12; tries++) {
            int i;
            if (tries == 0 && rnd.nextFloat() < 0.4f) {
                i = 0;
                float bd = 1e9f;
                for (int k = 0; k < pts.length; k++) {
                    float d = (pts[k][0] - px) * (pts[k][0] - px) + (pts[k][1] - pz) * (pts[k][1] - pz);
                    float de = (pts[k][0] - x) * (pts[k][0] - x) + (pts[k][1] - z) * (pts[k][1] - z);
                    if (de > 64f && d < bd) {
                        bd = d;
                        i = k;
                    }
                }
            } else {
                i = rnd.nextInt(pts.length);
            }
            float de = (pts[i][0] - x) * (pts[i][0] - x) + (pts[i][1] - z) * (pts[i][1] - z);
            if (de > 64f) {
                best = i;
                break;
            }
        }
        if (best < 0) best = rnd.nextInt(pts.length);
        tx = pts[best][0];
        tz = pts[best][1];
        pathLen = 0;
    }

    private void startChase() {
        state = CHASE;
        loseT = 0;
        restT = 0;
        legRaise = 0;
        repathT = 0;
    }

    public void update(Game g, float dt) {
        Player p = g.player;
        Level lv = g.level;
        float dx = p.x - x, dz = p.z - z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        boolean los = lv.los(x, z, p.x, p.z);
        // Со светом заметить проще; в патруле враг видит только в конусе перед собой (±75°).
        // Уже гонясь, держит цель в пределах 17 м прямой видимости. Первые секунды игры — «грация».
        // После 12 с погони «запыхавшийся» враг замирает на одной ноге (передышка 4,5 с): шанс оторваться.
        float senseR = p.flashOn ? 18f : 8f;
        if (p.sprinting) senseR += 5f;
        boolean inCone = true;
        if (state != CHASE && dist > 1e-3f) {
            float fx = -(float) Math.sin(yaw), fz = -(float) Math.cos(yaw);
            inCone = (fx * dx + fz * dz) / dist > 0.26f;
        }
        if (state == CHASE) senseR = 17f;
        boolean aware = g.time > 8f;
        sees = (aware && los && dist < senseR && inCone) || dist < 2.8f;
        boolean hears = aware && p.sprinting && dist < 12f;
        moving = false;
        stareCd -= dt;

        switch (state) {
            case PATROL:
                legRaise = Math.max(0, legRaise - dt * 3f);
                if (sees) {
                    startChase();
                } else if (hears) {
                    tx = p.x;
                    tz = p.z;
                    state = INVESTIGATE;
                    pathLen = 0;
                } else if (stareCd <= 0 && los && dist > 12f && dist < 34f) {
                    state = STARE;
                    timer = 3.5f + rnd.nextFloat() * 2.5f;
                    stareCd = 16f + rnd.nextFloat() * 10f;
                } else if (idleT > 0) {
                    idleT -= dt;
                } else if (goTo(g, tx, tz, 1.8f, dt, false)) {
                    idleT = 0.6f + rnd.nextFloat() * 2f;
                    pickPatrol(p.x, p.z);
                }
                break;
            case STARE:
                face(p.x, p.z, dt, 5f);
                legRaise = Math.min(1f, legRaise + dt * 2f);
                timer -= dt;
                if (sees && dist < 15f) startChase();
                else if (timer <= 0) state = PATROL;
                break;
            case INVESTIGATE:
                legRaise = Math.max(0, legRaise - dt * 3f);
                if (sees) {
                    startChase();
                } else if (goTo(g, tx, tz, 2.7f, dt, false)) {
                    state = SEARCH;
                    timer = 5f;
                }
                break;
            case SEARCH:
                timer -= dt;
                if (restT > 0) {
                    restT -= dt;
                    legRaise = Math.min(1f, legRaise + dt * 2f);
                    face(p.x, p.z, dt, 3f);
                    if (dist < 3.5f) startChase();
                    break;
                }
                legRaise = Math.max(0, legRaise - dt * 3f);
                yaw += dt * (float) Math.sin(timer * 1.7f) * 1.6f;
                if (sees) startChase();
                else if (timer <= 0) {
                    state = PATROL;
                    pickPatrol(p.x, p.z);
                }
                break;
            default: // CHASE
                if (sees) {
                    lastX = p.x;
                    lastZ = p.z;
                    loseT = 0;
                } else {
                    loseT += dt;
                }
                goTo(g, lastX, lastZ, chaseSpeed, dt, true);
                chaseClock += dt;
                if (loseT > 3f) {
                    tx = lastX;
                    tz = lastZ;
                    state = SEARCH;
                    timer = 6f;
                    chaseClock = 0;
                } else if (chaseClock > 12f && dist > 3.5f) {
                    state = SEARCH;
                    timer = 8f;
                    restT = 4.5f;
                    chaseClock = 0;
                    moving = false;
                }
                break;
        }
    }

    /** Шум (подобранный ключ и т.п.): враг идёт проверить место, если ещё не гонится. */
    public void alert(float nx, float nz) {
        if (state == CHASE) return;
        tx = nx;
        tz = nz;
        state = INVESTIGATE;
        pathLen = 0;
        legRaise = 0;
    }

    /** Поворот лицом к точке (для скримера). */
    public void lookAt(float px, float pz, float dt) {
        face(px, pz, dt, 14f);
    }

    private void face(float px, float pz, float dt, float rate) {
        float target = (float) Math.atan2(-(px - x), -(pz - z));
        yaw += M4.wrapAngle(target - yaw) * Math.min(1f, dt * rate);
    }

    /** Идёт к точке. Возвращает true, когда пришёл. */
    private boolean goTo(Game g, float gx, float gz, float speed, float dt, boolean chase) {
        Level lv = g.level;
        float ddx = gx - x, ddz = gz - z;
        float dd = (float) Math.sqrt(ddx * ddx + ddz * ddz);
        if (dd < (chase ? 0.3f : 0.7f)) return true;
        float wx, wz;
        if (lv.rayClear(x, z, gx, gz, R)) {
            wx = gx;
            wz = gz;
            pathLen = 0;
        } else {
            repathT -= dt;
            float gd = (gx - goalX) * (gx - goalX) + (gz - goalZ) * (gz - goalZ);
            if (repathT <= 0 || pathLen == 0 || gd > 4f || pathPos >= pathLen) {
                pathLen = g.nav.findPath(x, z, gx, gz, path);
                pathPos = 0;
                repathT = chase ? 0.35f : 1.2f;
                goalX = gx;
                goalZ = gz;
            }
            if (pathLen == 0) {
                wx = gx;
                wz = gz;
            } else {
                int best = Math.min(pathPos, pathLen - 1);
                int lim = Math.min(pathLen, pathPos + 10);
                for (int i = pathPos; i < lim; i++) {
                    if (lv.rayClear(x, z, path[2 * i], path[2 * i + 1], R * 0.9f)) best = i;
                    else break;
                }
                pathPos = best;
                wx = path[2 * best];
                wz = path[2 * best + 1];
                float ex = wx - x, ez = wz - z;
                if (ex * ex + ez * ez < 0.09f) pathPos = Math.min(pathLen, best + 1);
            }
        }
        float ex = wx - x, ez = wz - z, el = (float) Math.sqrt(ex * ex + ez * ez);
        if (el > 1e-4f) {
            float step = Math.min(el, speed * dt);
            tmp[0] = x + ex / el * step;
            tmp[1] = z + ez / el * step;
            lv.push(tmp, R);
            x = tmp[0];
            z = tmp[1];
            float target = (float) Math.atan2(-ex, -ez);
            yaw += M4.wrapAngle(target - yaw) * Math.min(1f, dt * 9f);
            moving = true;
            phase += dt * speed * 2.0f;
        }
        return false;
    }
}
