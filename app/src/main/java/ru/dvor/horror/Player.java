package ru.dvor.horror;

/** Герой: движение, выносливость, столкновения. */
public final class Player {
    public static final float R = 0.35f;
    public static final float WALK = 3.0f, RUN = 5.6f;
    private static final float SPRINT_SECONDS = 7f;

    public float x, z, yaw, vx, vz, speed;
    public float stamina = 1f;
    public float walkPhase;
    public boolean sprinting, flashOn = true, tired;
    private final float[] tmp = new float[2];

    public void reset(float px, float pz) {
        x = px;
        z = pz;
        yaw = 0;
        vx = vz = speed = 0;
        stamina = 1f;
        walkPhase = 0;
        sprinting = tired = false;
        flashOn = true;
    }

    public void update(Game g, Input in, float camYaw, float dt) {
        float mx = in.moveX, my = in.moveY;
        float len = (float) Math.sqrt(mx * mx + my * my);
        if (len > 1f) {
            mx /= len;
            my /= len;
            len = 1f;
        }
        boolean moving = len > 0.1f;
        if (tired && stamina > 0.3f) tired = false;
        sprinting = in.sprint && moving && !tired && stamina > 0f;
        if (sprinting) {
            stamina -= dt / SPRINT_SECONDS;
            if (stamina <= 0f) {
                stamina = 0f;
                tired = true;
                sprinting = false;
            }
        } else {
            stamina = Math.min(1f, stamina + dt / (moving ? 8f : 5f));
        }
        float sp = (sprinting ? RUN : WALK) * Math.min(1f, len * 1.25f);
        float s = (float) Math.sin(camYaw), c = (float) Math.cos(camYaw);
        float wx = c * mx - s * my, wz = -s * mx - c * my;
        float k = 1f - (float) Math.exp(-dt * 12f);
        vx += (wx * sp - vx) * k;
        vz += (wz * sp - vz) * k;
        tmp[0] = x + vx * dt;
        tmp[1] = z + vz * dt;
        g.level.push(tmp, R);
        float ax = tmp[0] - x, az = tmp[1] - z;
        x = tmp[0];
        z = tmp[1];
        speed = (float) Math.sqrt(ax * ax + az * az) / Math.max(dt, 1e-4f);
        if (speed > 0.4f) {
            float target = (float) Math.atan2(-vx, -vz);
            yaw += M4.wrapAngle(target - yaw) * Math.min(1f, dt * 10f);
            float before = walkPhase;
            walkPhase += dt * speed * 2.2f;
            if ((int) (walkPhase / Math.PI) != (int) (before / Math.PI)) g.sfx.step(sprinting);
        }
    }
}
