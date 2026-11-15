package ru.dvor.horror;

import java.util.Arrays;
import java.util.PriorityQueue;

/** Сетка 1x1 м по двору и поиск пути A* (8 направлений, без срезания углов). */
public final class Nav {
    public static final int W = (int) (Level.HX * 2), H = (int) (Level.HZ * 2);

    private final Level level;
    private final boolean[] block = new boolean[W * H];
    private final float[] g = new float[W * H];
    private final int[] from = new int[W * H];
    private final byte[] state = new byte[W * H];

    public Nav(Level level, float radius) {
        this.level = level;
        rebuild(radius);
    }

    public void rebuild(float radius) {
        for (int cz = 0; cz < H; cz++) {
            for (int cx = 0; cx < W; cx++) {
                block[cz * W + cx] = level.blocked(worldX(cx), worldZ(cz), radius);
            }
        }
    }

    public static int cellX(float x) {
        return Math.max(0, Math.min(W - 1, (int) Math.floor(x + Level.HX)));
    }

    public static int cellZ(float z) {
        return Math.max(0, Math.min(H - 1, (int) Math.floor(z + Level.HZ)));
    }

    public static float worldX(int cx) {
        return cx - Level.HX + 0.5f;
    }

    public static float worldZ(int cz) {
        return cz - Level.HZ + 0.5f;
    }

    public boolean isBlocked(int cx, int cz) {
        return cx < 0 || cz < 0 || cx >= W || cz >= H || block[cz * W + cx];
    }

    /** Ближайшая свободная клетка к заданной (спираль), либо -1. */
    private int nearestFree(int cx, int cz) {
        if (!isBlocked(cx, cz)) return cz * W + cx;
        for (int r = 1; r <= 5; r++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    if (!isBlocked(cx + dx, cz + dz)) return (cz + dz) * W + cx + dx;
                }
            }
        }
        return -1;
    }

    /**
     * Ищет путь. Результат — центры клеток (x,z,x,z,...) в out. Возвращает число точек (0 — пути нет).
     * out должен вмещать 2 * W * H значений (самый длинный возможный путь).
     */
    public int findPath(float sx, float sz, float gx, float gz, float[] out) {
        int s = nearestFree(cellX(sx), cellZ(sz));
        int t = nearestFree(cellX(gx), cellZ(gz));
        if (s < 0 || t < 0) return 0;
        Arrays.fill(state, (byte) 0);
        int tx = t % W, tz = t / W;
        PriorityQueue<Long> open = new PriorityQueue<>();
        g[s] = 0;
        from[s] = -1;
        state[s] = 1;
        open.add(key(0, s));
        while (!open.isEmpty()) {
            int cur = (int) (open.poll() & 0xFFFFF);
            if (state[cur] == 2) continue;
            state[cur] = 2;
            if (cur == t) break;
            int cx = cur % W, cz = cur / W;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) continue;
                    int nx = cx + dx, nz = cz + dz;
                    if (isBlocked(nx, nz)) continue;
                    if (dx != 0 && dz != 0 && (isBlocked(cx + dx, cz) || isBlocked(cx, cz + dz))) continue;
                    int ni = nz * W + nx;
                    if (state[ni] == 2) continue;
                    float ng = g[cur] + (dx != 0 && dz != 0 ? 1.4142f : 1f);
                    if (state[ni] == 1 && ng >= g[ni]) continue;
                    g[ni] = ng;
                    from[ni] = cur;
                    state[ni] = 1;
                    float hx = Math.abs(nx - tx), hz = Math.abs(nz - tz);
                    float h = Math.max(hx, hz) + 0.4142f * Math.min(hx, hz);
                    open.add(key(ng + h, ni));
                }
            }
        }
        if (state[t] != 2) return 0;
        int count = 0;
        for (int c = t; c >= 0; c = from[c]) count++;
        int i = count - 1;
        for (int c = t; c >= 0; c = from[c], i--) {
            out[i * 2] = worldX(c % W);
            out[i * 2 + 1] = worldZ(c / W);
        }
        return count;
    }

    private static long key(float f, int idx) {
        return ((long) (f * 1000f) << 20) | idx;
    }
}
