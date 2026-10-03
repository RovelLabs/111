package dev.pulseclient.event.events;

import dev.pulseclient.event.Event;

/** Нажатие кнопки мыши в игре (не в меню). button — GLFW_MOUSE_BUTTON_*, action — GLFW_PRESS / GLFW_RELEASE. */
public final class MouseEvent extends Event {
    private final int button;
    private final int action;

    public MouseEvent(int button, int action) {
        this.button = button;
        this.action = action;
    }

    public int button() {
        return button;
    }

    public int action() {
        return action;
    }
}
