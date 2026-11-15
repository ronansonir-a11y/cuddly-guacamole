package ru.dvor.horror;

/** Звуковые события игры (реализация — Audio; в тестах — заглушка). */
public interface Sfx {
    void step(boolean run);

    void keyPickup();

    void gateUnlock();

    void scare();

    void creak();

    /** fear 0..1 — сердцебиение и шум; near 0..1 — низкий гул рядом с врагом. */
    void ambient(float fear, float near);

    void pause(boolean paused);
}
