package ru.dvor.horror;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/** Звук без файлов: все эффекты синтезируются при старте, микшер пишет в AudioTrack из отдельного потока. */
public final class Audio implements Sfx {
    private static final int RATE = 22050;

    private static final class Voice {
        short[] data;
        float pos, step = 1f, vol, target;
        boolean loop, active = true, persistent;
    }

    private interface Gen {
        float at(float t);
    }

    private final ArrayList<Voice> voices = new ArrayList<>();
    private final Random rnd = new Random(7);
    private AudioTrack track;
    private volatile boolean running, paused;
    private Thread thread;

    private final Voice wind, heart, drone;
    private final short[] stepWalk, stepRun, key, gate, scare, creak;

    public Audio() {
        wind = loopVoice(makeWind());
        heart = loopVoice(makeHeart());
        drone = loopVoice(makeDrone());
        wind.target = 0.35f;
        stepWalk = makeStep(0.16f, 26f);
        stepRun = makeStep(0.2f, 20f);
        key = makeKey();
        gate = makeGate();
        scare = makeScare();
        creak = makeCreak();
        try {
            int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 8192), AudioTrack.MODE_STREAM);
            track.play();
            running = true;
            thread = new Thread(new Runnable() {
                public void run() {
                    mixLoop();
                }
            }, "audio-mixer");
            thread.setDaemon(true);
            thread.start();
        } catch (Throwable t) {
            track = null;
            running = false;
        }
    }

    private Voice loopVoice(short[] d) {
        Voice v = new Voice();
        v.data = d;
        v.loop = true;
        v.persistent = true;
        synchronized (voices) {
            voices.add(v);
        }
        return v;
    }

    private void once(short[] d, float vol, float rate) {
        if (!running) return;
        Voice v = new Voice();
        v.data = d;
        v.vol = vol;
        v.target = vol;
        v.step = rate;
        synchronized (voices) {
            if (voices.size() < 16) voices.add(v);
        }
    }

    private void mixLoop() {
        final int chunk = 512;
        short[] out = new short[chunk];
        float[] mix = new float[chunk];
        while (running) {
            if (paused) {
                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                    return;
                }
                continue;
            }
            Arrays.fill(mix, 0f);
            synchronized (voices) {
                for (int i = voices.size() - 1; i >= 0; i--) {
                    Voice v = voices.get(i);
                    if (!v.active) {
                        if (!v.persistent) voices.remove(i);
                        continue;
                    }
                    v.vol += (v.target - v.vol) * 0.3f;
                    int n = v.data.length;
                    float pos = v.pos;
                    for (int k = 0; k < chunk; k++) {
                        if (pos >= n) {
                            if (v.loop) {
                                pos -= n;
                            } else {
                                v.active = false;
                                break;
                            }
                        }
                        mix[k] += v.data[(int) pos] * (1f / 32768f) * v.vol;
                        pos += v.step;
                    }
                    v.pos = pos;
                }
            }
            for (int k = 0; k < chunk; k++) {
                float x = mix[k];
                x = x / (1f + Math.abs(x) * 0.6f);
                out[k] = (short) (x * 30000f);
            }
            try {
                track.write(out, 0, chunk);
            } catch (Throwable t) {
                return;
            }
        }
    }

    // ------------------------------------------------------------------ Sfx

    @Override
    public void step(boolean run) {
        once(run ? stepRun : stepWalk, run ? 0.55f : 0.35f, 0.9f + rnd.nextFloat() * 0.25f);
    }

    @Override
    public void keyPickup() {
        once(key, 0.7f, 1f);
    }

    @Override
    public void gateUnlock() {
        once(gate, 0.8f, 1f);
    }

    @Override
    public void scare() {
        once(scare, 1f, 1f);
    }

    @Override
    public void creak() {
        once(creak, 0.35f, 0.85f + rnd.nextFloat() * 0.3f);
    }

    @Override
    public void ambient(float fear, float near) {
        heart.target = fear < 0.05f ? 0f : 0.25f + 0.75f * fear;
        heart.step = 0.8f + fear * 1.0f;
        drone.target = near * near * 0.6f;
    }

    @Override
    public void pause(boolean p) {
        paused = p;
    }

    public void release() {
        running = false;
        if (thread != null) thread.interrupt();
        try {
            if (track != null) {
                track.stop();
                track.release();
            }
        } catch (Throwable ignored) {
        }
        track = null;
    }

    // ------------------------------------------------------------------ синтез

    private short[] gen(float seconds, Gen g) {
        short[] d = new short[(int) (seconds * RATE)];
        for (int i = 0; i < d.length; i++) {
            float x = g.at(i / (float) RATE);
            d[i] = (short) (Math.max(-1f, Math.min(1f, x)) * 32000f);
        }
        return d;
    }

    private float noise() {
        return rnd.nextFloat() * 2f - 1f;
    }

    private short[] makeWind() {
        final int n = RATE * 6, f = RATE / 2;
        float[] b = new float[n + f];
        float y1 = 0, y2 = 0;
        for (int i = 0; i < b.length; i++) {
            y1 += (noise() - y1) * 0.03f;
            y2 += (y1 - y2) * 0.08f;
            float t = i / (float) RATE;
            float am = 0.55f + 0.45f * (float) Math.sin(2 * Math.PI * t / 6.0) * (float) Math.sin(2 * Math.PI * t / 3.0 + 1.0);
            b[i] = y2 * 5f * am;
        }
        short[] d = new short[n];
        for (int i = 0; i < n; i++) {
            float v = i < f ? b[i] * (i / (float) f) + b[n + i] * (1f - i / (float) f) : b[i];
            d[i] = (short) (Math.max(-1f, Math.min(1f, v)) * 20000f);
        }
        return d;
    }

    private static float thump(float t, float t0, float f, float decay) {
        if (t < t0) return 0f;
        float u = t - t0;
        return (float) (Math.sin(2 * Math.PI * f * u) * Math.exp(-u * decay));
    }

    private short[] makeHeart() {
        return gen(1.0f, new Gen() {
            public float at(float t) {
                return 1.3f * thump(t, 0f, 55f, 16f) + 0.9f * thump(t, 0.27f, 46f, 18f);
            }
        });
    }

    private short[] makeDrone() {
        return gen(4.0f, new Gen() {
            public float at(float t) {
                float trem = 0.7f + 0.3f * (float) Math.sin(2 * Math.PI * 3.0 * t);
                return trem * 0.28f * ((float) Math.sin(2 * Math.PI * 55.0 * t) + (float) Math.sin(2 * Math.PI * 58.5 * t)
                        + 0.5f * (float) Math.sin(2 * Math.PI * 110.25 * t));
            }
        });
    }

    private short[] makeStep(float len, final float decay) {
        final float[] lp = {0f};
        return gen(len, new Gen() {
            public float at(float t) {
                lp[0] += (noise() - lp[0]) * 0.3f;
                return lp[0] * (float) Math.exp(-t * decay) * 1.4f;
            }
        });
    }

    private short[] makeKey() {
        return gen(0.9f, new Gen() {
            public float at(float t) {
                return 0.4f * ((float) (Math.sin(2 * Math.PI * 880 * t) * Math.exp(-t * 4.5)
                        + 0.6 * Math.sin(2 * Math.PI * 1318.5 * t) * Math.exp(-t * 6.0)
                        + 0.4 * Math.sin(2 * Math.PI * 1760 * t) * Math.exp(-t * 9.0)));
            }
        });
    }

    private short[] makeGate() {
        final float[] lp = {0f};
        return gen(1.2f, new Gen() {
            public float at(float t) {
                lp[0] += (noise() - lp[0]) * 0.5f;
                float clank = lp[0] * (float) Math.exp(-t * 25.0) * 1.2f;
                float thud = (float) (Math.sin(2 * Math.PI * 70 * t) * Math.exp(-t * 10.0));
                float f = 180f + 360f * Math.min(1f, t / 0.9f);
                float rise = 0.25f * (float) (Math.sin(2 * Math.PI * f * t) * Math.max(0f, 1f - t / 1.2f)) * Math.min(1f, t * 8f);
                return clank + thud * 0.9f + rise;
            }
        });
    }

    private short[] makeScare() {
        return gen(1.5f, new Gen() {
            public float at(float t) {
                float f = 1500f * (float) Math.exp(-t * 1.9) + 160f;
                float vib = 1f + 0.04f * (float) Math.sin(2 * Math.PI * 28 * t);
                float ph = (f * vib * t) % 1f;
                float saw = ph * 2f - 1f;
                float burst = noise() * (float) Math.exp(-t * 7.0);
                float env = Math.min(1f, (1.5f - t) / 0.35f);
                float x = (saw * 0.7f + burst * 1.2f + noise() * 0.25f) * env;
                return Math.max(-1f, Math.min(1f, x * 2.2f));
            }
        });
    }

    private short[] makeCreak() {
        final float[] ph = {0f};
        return gen(1.3f, new Gen() {
            public float at(float t) {
                float f = 130f + 90f * (float) Math.sin(t * 2.4) + 25f * noise();
                ph[0] = (ph[0] + f / RATE) % 1f;
                float sq = ph[0] < 0.5f ? 1f : -1f;
                float env = (float) Math.sin(Math.PI * Math.min(1f, t / 1.3f));
                return sq * 0.35f * env * env;
            }
        });
    }
}
