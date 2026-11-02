package io.github.mcheliceport.shim.client;

import org.lwjgl.glfw.GLFW;

/** 26.2's key event record, built from 1.21.1's {@code keyPressed(key, scancode, modifiers)}. */
public record KeyEvent(int key, int scancode, int modifiers) {
    public boolean isEscape() {
        return key == GLFW.GLFW_KEY_ESCAPE;
    }

    public boolean isConfirmation() {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
    }

    public boolean hasControlDown() {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    public boolean hasShiftDown() {
        return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
    }

    public boolean hasAltDown() {
        return (modifiers & GLFW.GLFW_MOD_ALT) != 0;
    }

    public int input() {
        return key;
    }
}
