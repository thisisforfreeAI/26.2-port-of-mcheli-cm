package io.github.mcheliceport.shim.client.event;

/** NeoForge's client tick events. */
public abstract class ClientTickEvent extends ClientEvent {
    public static final class Pre extends ClientTickEvent {
    }

    public static final class Post extends ClientTickEvent {
    }
}
