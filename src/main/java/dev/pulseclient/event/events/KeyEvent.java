package dev.pulseclient.event.events;

import dev.pulseclient.event.Event;

/**
 * Нажатие клавиши, когда не открыт ни один экран (чат, инвентарь, меню).
 * key — код GLFW, action — GLFW_PRESS / GLFW_RELEASE / GLFW_REPEAT.
 */
public final class KeyEvent extends Event {
    private final int key;
    private final int action;

    public KeyEvent(int key, int action) {
        this.key = key;
        this.action = action;
    }

    public int key() {
        return key;
    }

    public int action() {
        return action;
    }
}
