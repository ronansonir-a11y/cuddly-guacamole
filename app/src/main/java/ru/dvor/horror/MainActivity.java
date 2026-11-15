package ru.dvor.horror;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;

/** Экраны: меню, пауза, поимка/победа. Игра рисуется в GLSurfaceView, поверх — HudView и оверлеи. */
public final class MainActivity extends Activity implements Game.Listener {
    private Game game;
    private Audio audio;
    private GLSurfaceView glView;
    private HudView hud;
    private FrameLayout root;
    private View titleView, pauseView, resultView;
    private ImageView scareView;
    private TextView resultTitle, resultInfo;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private float d;
    private Bitmap scareBmp;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        d = getResources().getDisplayMetrics().density;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        game = new Game(System.nanoTime());
        audio = new Audio();
        game.sfx = audio;
        game.listener = this;

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        glView.setPreserveEGLContextOnPause(true);
        glView.setRenderer(new Renderer(this, game));
        glView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        // ограничиваем разрешение рендера (экономия батареи и FPS на среднем железе)
        int sw = getResources().getDisplayMetrics().widthPixels, sh = getResources().getDisplayMetrics().heightPixels;
        float k = Math.min(1f, 1280f / Math.max(sw, sh));
        glView.getHolder().setFixedSize(Math.max(1, (int) (sw * k)), Math.max(1, (int) (sh * k)));
        root.addView(glView, new FrameLayout.LayoutParams(-1, -1));

        hud = new HudView(this, game);
        hud.setPortrait(loadBitmap("ui/ui_hero.png"));
        hud.setOnPause(new Runnable() {
            public void run() {
                pauseGame();
            }
        });
        hud.setActive(false);
        root.addView(hud, new FrameLayout.LayoutParams(-1, -1));

        scareBmp = loadBitmap("ui/ui_scare.jpg");
        scareView = new ImageView(this);
        scareView.setBackgroundColor(Color.BLACK);
        scareView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        scareView.setImageBitmap(scareBmp);
        scareView.setVisibility(View.GONE);
        root.addView(scareView, new FrameLayout.LayoutParams(-1, -1));

        pauseView = buildPause();
        resultView = buildResult();
        titleView = buildTitle();
        root.addView(pauseView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(resultView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(titleView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        showTitle();
    }

    // ------------------------------------------------------------------ вспомогательное

    private Bitmap loadBitmap(String path) {
        try (InputStream in = getAssets().open(path)) {
            return BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            return null;
        }
    }

    private void applyImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private TextView label(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setShadowLayer(6 * d, 0, 0, Color.BLACK);
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(17);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.argb(210, 110, 8, 14));
        g.setStroke((int) (2 * d), Color.rgb(210, 36, 46));
        g.setCornerRadius(10 * d);
        b.setBackground(g);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (240 * d), (int) (52 * d));
        lp.topMargin = (int) (10 * d);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        return c;
    }

    private ImageView portrait(String asset, int sizeDp) {
        ImageView v = new ImageView(this);
        v.setImageBitmap(loadBitmap(asset));
        v.setLayoutParams(new LinearLayout.LayoutParams((int) (sizeDp * d), (int) (sizeDp * d)));
        return v;
    }

    private void show(View v, boolean on) {
        v.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ экраны

    private View buildTitle() {
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(Color.BLACK);
        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setImageBitmap(loadBitmap("ui/ui_bg.jpg"));
        f.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        View dim = new View(this);
        dim.setBackgroundColor(Color.argb(110, 0, 0, 0));
        f.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (56 * d), (int) (24 * d), (int) (56 * d), (int) (24 * d));

        LinearLayout left = column();
        left.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        TextView title = label("НОЧНОЙ ДВОР", 40, Color.rgb(205, 24, 34));
        title.setLetterSpacing(0.08f);
        left.addView(title);
        TextView sub = label("Собери 5 ключей, открой ворота и добеги до подъезда.\nКудрявый с сумкой уже вышел во двор.", 14, Color.rgb(225, 225, 230));
        sub.setPadding(0, (int) (6 * d), 0, (int) (14 * d));
        left.addView(sub);
        left.addView(button("ИГРАТЬ", new View.OnClickListener() {
            public void onClick(View v) {
                startGame();
            }
        }));
        left.addView(button("КАК ИГРАТЬ", new View.OnClickListener() {
            public void onClick(View v) {
                showHelp();
            }
        }));
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout right = column();
        right.setGravity(Gravity.CENTER);
        right.addView(portrait("ui/ui_hero.png", 120));
        right.addView(label("ТЫ", 14, Color.WHITE));
        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(1, (int) (14 * d)));
        right.addView(gap);
        right.addView(portrait("ui/ui_enemy.png", 92));
        right.addView(label("ОН", 14, Color.rgb(205, 24, 34)));
        row.addView(right, new LinearLayout.LayoutParams((int) (170 * d), -2));
        f.addView(row, new FrameLayout.LayoutParams(-1, -1));
        f.setClickable(true);
        return f;
    }

    private View buildPause() {
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(Color.argb(190, 0, 0, 0));
        LinearLayout c = column();
        c.addView(label("ПАУЗА", 34, Color.WHITE));
        c.addView(button("ПРОДОЛЖИТЬ", new View.OnClickListener() {
            public void onClick(View v) {
                resumeGame();
            }
        }));
        c.addView(button("В МЕНЮ", new View.OnClickListener() {
            public void onClick(View v) {
                showTitle();
            }
        }));
        f.addView(c, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        f.setClickable(true);
        f.setVisibility(View.GONE);
        return f;
    }

    private View buildResult() {
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(Color.argb(215, 0, 0, 0));
        LinearLayout c = column();
        resultTitle = label("", 36, Color.rgb(205, 24, 34));
        resultInfo = label("", 15, Color.WHITE);
        resultInfo.setPadding(0, (int) (4 * d), 0, (int) (12 * d));
        resultInfo.setGravity(Gravity.CENTER);
        c.addView(resultTitle);
        c.addView(resultInfo);
        c.addView(button("ЕЩЁ РАЗ", new View.OnClickListener() {
            public void onClick(View v) {
                startGame();
            }
        }));
        c.addView(button("В МЕНЮ", new View.OnClickListener() {
            public void onClick(View v) {
                showTitle();
            }
        }));
        f.addView(c, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        f.setClickable(true);
        f.setVisibility(View.GONE);
        return f;
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Как играть")
                .setMessage("• Левая половина экрана — стик ходьбы.\n"
                        + "• Правая половина — осмотр (камера).\n"
                        + "• БЕГ — ускорение, тратит силы.\n"
                        + "• СВЕТ — фонарик. Со светом тебя замечают издалека, без света — только вблизи.\n"
                        + "• Подбери 5 светящихся ключей — ворота на севере откроются.\n"
                        + "• Беги в подъезд. Если враг потерял тебя из виду — замри в темноте.")
                .setPositiveButton("Понятно", null)
                .show();
    }

    // ------------------------------------------------------------------ потоки состояний

    private void showTitle() {
        ui.removeCallbacksAndMessages(null);
        game.paused = true;
        hud.setActive(false);
        show(scareView, false);
        show(pauseView, false);
        show(resultView, false);
        show(titleView, true);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    private void startGame() {
        ui.removeCallbacksAndMessages(null);
        show(titleView, false);
        show(resultView, false);
        show(pauseView, false);
        show(scareView, false);
        game.paused = false;
        glView.queueEvent(new Runnable() {
            public void run() {
                game.start();
            }
        });
        hud.setActive(true);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    private void pauseGame() {
        if (game.state != Game.PLAYING || game.paused) return;
        game.paused = true;
        game.input.reset();
        hud.setActive(false);
        show(pauseView, true);
    }

    private void resumeGame() {
        show(pauseView, false);
        game.paused = false;
        hud.setActive(true);
    }

    @Override
    public void onKey(int have, int total) {
    }

    @Override
    public void onGateOpen() {
    }

    @Override
    public void onCaught() {
        runOnUiThread(new Runnable() {
            public void run() {
                hud.setActive(false);
                buzz(new long[]{0, 90, 40, 260});
                ui.postDelayed(new Runnable() {
                    public void run() {
                        showScare();
                    }
                }, 450);
            }
        });
    }

    @Override
    public void onWin(final float seconds) {
        runOnUiThread(new Runnable() {
            public void run() {
                hud.setActive(false);
                int s = (int) seconds;
                resultTitle.setText("ТЫ ВЫБРАЛСЯ!");
                resultTitle.setTextColor(Color.rgb(120, 255, 140));
                resultInfo.setText(String.format("Ты успел до подъезда.\nВремя: %d:%02d", s / 60, s % 60));
                show(resultView, true);
            }
        });
    }

    private void showScare() {
        if (game.state != Game.CAUGHT) return;
        show(scareView, true);
        final long t0 = System.currentTimeMillis();
        ui.post(new Runnable() {
            public void run() {
                long dt = System.currentTimeMillis() - t0;
                if (dt > 1100 || scareView.getVisibility() != View.VISIBLE) {
                    show(scareView, false);
                    resultTitle.setText("ОН ТЕБЯ ПОЙМАЛ");
                    resultTitle.setTextColor(Color.rgb(205, 24, 34));
                    resultInfo.setText("Ключей собрано: " + game.keysHave + " из " + Game.KEYS_TOTAL
                            + "\nСовет: фонарик выдаёт тебя издалека.");
                    show(resultView, true);
                    return;
                }
                float j = 22 * d;
                scareView.setTranslationX((float) (Math.random() - 0.5) * j);
                scareView.setTranslationY((float) (Math.random() - 0.5) * j);
                float sc = 1.04f + (float) Math.random() * 0.08f;
                scareView.setScaleX(sc);
                scareView.setScaleY(sc);
                ui.postDelayed(this, 30);
            }
        });
    }

    private void buzz(long[] pattern) {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(pattern, -1);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ жизненный цикл

    @Override
    public void onBackPressed() {
        if (titleView.getVisibility() == View.VISIBLE) {
            super.onBackPressed();
        } else if (pauseView.getVisibility() == View.VISIBLE) {
            resumeGame();
        } else if (game.state == Game.PLAYING) {
            pauseGame();
        } else {
            showTitle();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    @Override
    protected void onPause() {
        super.onPause();
        audio.pause(true);
        if (game.state == Game.PLAYING && !game.paused) pauseGame();
        glView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        glView.onResume();
        audio.pause(false);
        applyImmersive();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        audio.release();
    }
}
