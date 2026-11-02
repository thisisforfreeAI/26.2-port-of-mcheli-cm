package io.github.mcheliceport.platform.fabric.inject;

/** NeoForge's additions to {@code MouseHandler}: the mouse movement not yet turned into look. */
public interface MouseHandlerExtras {
    default double getXVelocity() {
        throw new AssertionError("implemented by MouseHandlerMixin");
    }

    default double getYVelocity() {
        throw new AssertionError("implemented by MouseHandlerMixin");
    }
}
