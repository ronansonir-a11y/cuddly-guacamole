package ru.dvor.horror;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/** Прозрачный слой поверх GL: сенсорное управление (стик, взгляд, кнопки) и HUD. */
public final class HudView extends View {
    private final Game game;
    private final Input input;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private Bitmap portrait;
    private Runnable onPause;
    private boolean active;

    private int joyId = -1, lookId = -1, sprintId = -1;
    private float joyCx, joyCy, joyX, joyY, lastLx, lastLy;
    private float sprintX, sprintY, sprintR, flashX, flashY, flashR, pauseX, pauseY, pauseR, joyR;

    public HudView(Context c, Game game) {
        super(c);
        this.game = game;
        this.input = game.input;
        d = c.getResources().getDisplayMetrics().density;
        joyR = 62 * d;
        setFocusable(false);
    }

    public void setPortrait(Bitmap b) {
        portrait = b;
    }

    public void setOnPause(Runnable r) {
        onPause = r;
    }

    public void setActive(boolean a) {
        active = a;
        if (!a) releaseAll();
        setVisibility(a ? VISIBLE : INVISIBLE);
        if (a) postInvalidate();
    }

    private void releaseAll() {
        joyId = lookId = sprintId = -1;
        joyX = joyY = 0;
        input.reset();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        sprintR = 46 * d;
        sprintX = w - 96 * d;
        sprintY = h - 84 * d;
        flashR = 34 * d;
        flashX = w - 190 * d;
        flashY = h - 56 * d;
        pauseR = 22 * d;
        pauseX = w - 40 * d;
        pauseY = 40 * d;
    }

    private static boolean hit(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!active) return false;
        int a = e.getActionMasked();
        int idx = e.getActionIndex();
        switch (a) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                down(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) move(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                up(e.getPointerId(idx));
                break;
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                break;
            default:
                break;
        }
        return true;
    }

    private void down(int id, float x, float y) {
        if (hit(x, y, pauseX, pauseY, pauseR * 1.4f)) {
            if (onPause != null) onPause.run();
        } else if (hit(x, y, sprintX, sprintY, sprintR * 1.25f)) {
            sprintId = id;
            input.sprint = true;
        } else if (hit(x, y, flashX, flashY, flashR * 1.3f)) {
            input.tapFlash();
        } else if (x < getWidth() * 0.45f && joyId < 0) {
            joyId = id;
            joyCx = x;
            joyCy = y;
            joyX = joyY = 0;
        } else if (lookId < 0) {
            lookId = id;
            lastLx = x;
            lastLy = y;
        }
    }

    private void move(int id, float x, float y) {
        if (id == joyId) {
            float dx = x - joyCx, dy = y - joyCy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > joyR) {
                dx = dx / len * joyR;
                dy = dy / len * joyR;
            }
            joyX = dx;
            joyY = dy;
            float mx = dx / joyR, my = -dy / joyR;
            input.moveX = Math.abs(mx) < 0.08f ? 0f : mx;
            input.moveY = Math.abs(my) < 0.08f ? 0f : my;
        } else if (id == lookId) {
            input.addLook(x - lastLx, y - lastLy);
            lastLx = x;
            lastLy = y;
        }
    }

    private void up(int id) {
        if (id == joyId) {
            joyId = -1;
            joyX = joyY = 0;
            input.moveX = 0;
            input.moveY = 0;
        } else if (id == lookId) {
            lookId = -1;
        } else if (id == sprintId) {
            sprintId = -1;
            input.sprint = false;
        }
    }

    // ------------------------------------------------------------------ рисование

    private void text(Canvas c, String s, float x, float y, float sp, int color, int alpha) {
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(sp * d);
        p.setShadowLayer(4 * d, 0, 0, Color.BLACK);
        p.setColor(color);
        p.setAlpha(alpha);
        c.drawText(s, x, y, p);
        p.clearShadowLayer();
    }

    private void button(Canvas c, float x, float y, float r, boolean on, int color, String label) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(on ? color : Color.WHITE);
        p.setAlpha(on ? 110 : 38);
        c.drawCircle(x, y, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2 * d);
        p.setColor(on ? color : Color.WHITE);
        p.setAlpha(on ? 230 : 110);
        c.drawCircle(x, y, r, p);
        text(c, label, x, y + 5 * d, 12, Color.WHITE, 220);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!active) return;
        if (game.state == Game.PLAYING) drawHud(c);
        postInvalidateDelayed(33);
    }

    private void drawHud(Canvas c) {
        int w = getWidth(), h = getHeight();
        float fear = game.fear;
        float t = game.time;

        // стик: плавающий, до касания — призрачная подсказка
        float bx = joyId >= 0 ? joyCx : 110 * d, by = joyId >= 0 ? joyCy : h - 110 * d;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2 * d);
        p.setColor(Color.WHITE);
        p.setAlpha(joyId >= 0 ? 110 : 45);
        c.drawCircle(bx, by, joyR, p);
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(joyId >= 0 ? 150 : 50);
        c.drawCircle(bx + joyX, by + joyY, 27 * d, p);

        button(c, sprintX, sprintY, sprintR, input.sprint, game.player.tired ? Color.rgb(220, 60, 50) : Color.rgb(255, 255, 255), "БЕГ");
        button(c, flashX, flashY, flashR, game.flashOn, Color.rgb(255, 214, 90), "СВЕТ");

        // пауза
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        p.setAlpha(150);
        c.drawRect(pauseX - 8 * d, pauseY - 10 * d, pauseX - 3 * d, pauseY + 10 * d, p);
        c.drawRect(pauseX + 3 * d, pauseY - 10 * d, pauseX + 8 * d, pauseY + 10 * d, p);

        // портрет героя: дрожит и краснеет от страха
        float size = 66 * d, px = 16 * d, py = 14 * d;
        float sx = fear > 0.3f ? (float) Math.sin(t * 43.0) * fear * 3 * d : 0f;
        float sy = fear > 0.3f ? (float) Math.cos(t * 51.0) * fear * 3 * d : 0f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.BLACK);
        p.setAlpha(150);
        c.drawCircle(px + size / 2 + sx, py + size / 2 + sy, size / 2, p);
        if (portrait != null) {
            p.setAlpha(255);
            c.drawBitmap(portrait, null, new android.graphics.RectF(px + sx, py + sy, px + size + sx, py + size + sy), p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * d);
        p.setColor(Color.rgb(200, 20, 30));
        p.setAlpha((int) (60 + 195 * Math.min(1f, fear * 1.4f)));
        c.drawCircle(px + size / 2 + sx, py + size / 2 + sy, size / 2, p);

        // выносливость
        float bw = size, bh = 6 * d, by2 = py + size + 8 * d;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.BLACK);
        p.setAlpha(140);
        c.drawRect(px, by2, px + bw, by2 + bh, p);
        p.setColor(game.player.tired ? Color.rgb(220, 60, 50) : Color.rgb(230, 230, 230));
        p.setAlpha(210);
        c.drawRect(px, by2, px + bw * Math.max(0f, Math.min(1f, game.stamina)), by2 + bh, p);

        // ключи
        for (int i = 0; i < Game.KEYS_TOTAL; i++) {
            float kx = px + size + 22 * d + i * 24 * d, ky = py + 22 * d;
            boolean got = i < game.keysHave;
            p.setStyle(Paint.Style.FILL);
            p.setColor(got ? Color.rgb(255, 200, 50) : Color.BLACK);
            p.setAlpha(got ? 240 : 120);
            c.drawCircle(kx, ky, 8 * d, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f * d);
            p.setColor(Color.rgb(255, 200, 50));
            p.setAlpha(got ? 255 : 140);
            c.drawCircle(kx, ky, 8 * d, p);
        }
        String goal = game.gateOpen ? "Беги в подъезд!" : "Ключи: " + game.keysHave + " / " + Game.KEYS_TOTAL;
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(13 * d);
        p.setStyle(Paint.Style.FILL);
        p.setShadowLayer(3 * d, 0, 0, Color.BLACK);
        p.setColor(game.gateOpen ? Color.rgb(120, 255, 140) : Color.WHITE);
        p.setAlpha(230);
        c.drawText(goal, px + size + 14 * d, py + 54 * d, p);
        p.clearShadowLayer();

        // подсказка
        float ht = game.hintT;
        if (ht > 0f && game.hint.length() > 0) {
            text(c, game.hint, w / 2f, h - 46 * d, 17, Color.WHITE, (int) (255 * Math.min(1f, ht / 0.6f)));
        }
    }
}
