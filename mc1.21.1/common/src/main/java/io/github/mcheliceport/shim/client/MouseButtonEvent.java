package io.github.mcheliceport.shim.client;

/** 26.2's mouse button event record, built from 1.21.1's {@code mouseClicked(x, y, button)}. */
public record MouseButtonEvent(double x, double y, int button) {
    public int input() {
        return button;
    }
}
