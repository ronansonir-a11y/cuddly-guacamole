package ru.dvor.horror;

/** Состояние ввода: пишется из UI-потока, читается из GL-потока. */
public final class Input {
    /** Джойстик: x вправо, y вперёд, диапазон -1..1. */
    public volatile float moveX, moveY;
    public volatile boolean sprint;

    private float lookDx, lookDy;
    private boolean flashTap;

    public synchronized void addLook(float dx, float dy) {
        lookDx += dx;
        lookDy += dy;
    }

    public synchronized void takeLook(float[] out) {
        out[0] = lookDx;
        out[1] = lookDy;
        lookDx = 0;
        lookDy = 0;
    }

    public synchronized void tapFlash() {
        flashTap = true;
    }

    public synchronized boolean takeFlash() {
        boolean r = flashTap;
        flashTap = false;
        return r;
    }

    public synchronized void reset() {
        moveX = 0;
        moveY = 0;
        sprint = false;
        lookDx = 0;
        lookDy = 0;
        flashTap = false;
    }
}
