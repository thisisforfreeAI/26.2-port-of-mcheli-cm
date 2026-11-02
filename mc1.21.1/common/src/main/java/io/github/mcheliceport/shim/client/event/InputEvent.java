package io.github.mcheliceport.shim.client.event;

import io.github.mcheliceport.shim.client.KeyEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionHand;

/** NeoForge's input events. */
public abstract class InputEvent extends ClientEvent {

    public static final class Key extends InputEvent {
        private final KeyEvent key;
        private final int action;

        public Key(int key, int scancode, int action, int modifiers) {
            this.key = new KeyEvent(key, scancode, modifiers);
            this.action = action;
        }

        public KeyEvent getKeyEvent() {
            return key;
        }

        public int getKey() {
            return key.key();
        }

        public int getAction() {
            return action;
        }
    }

    public static final class MouseScrollingEvent extends InputEvent {
        private final double deltaX;
        private final double deltaY;

        public MouseScrollingEvent(double deltaX, double deltaY) {
            this.deltaX = deltaX;
            this.deltaY = deltaY;
        }

        public double getScrollDeltaX() {
            return deltaX;
        }

        public double getScrollDeltaY() {
            return deltaY;
        }
    }

    /** The attack, use or pick key was pressed. {@link #setCanceled} stops vanilla's handling. */
    public static final class InteractionKeyMappingTriggered extends InputEvent {
        private final int button;
        private final KeyMapping keyMapping;
        private final InteractionHand hand;
        private boolean swingHand = true;

        public InteractionKeyMappingTriggered(int button, KeyMapping keyMapping, InteractionHand hand) {
            this.button = button;
            this.keyMapping = keyMapping;
            this.hand = hand;
        }

        public boolean isAttack() {
            return button == 0;
        }

        public boolean isUseItem() {
            return button == 1;
        }

        public boolean isPickBlock() {
            return button == 2;
        }

        public KeyMapping getKeyMapping() {
            return keyMapping;
        }

        public InteractionHand getHand() {
            return hand;
        }

        public void setSwingHand(boolean swing) {
            this.swingHand = swing;
        }

        public boolean shouldSwingHand() {
            return swingHand;
        }
    }
}
