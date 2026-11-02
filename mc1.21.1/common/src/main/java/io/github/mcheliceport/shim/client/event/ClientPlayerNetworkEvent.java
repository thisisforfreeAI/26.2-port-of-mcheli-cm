package io.github.mcheliceport.shim.client.event;

/** NeoForge's client connection events. */
public abstract class ClientPlayerNetworkEvent extends ClientEvent {
    public static final class LoggingIn extends ClientPlayerNetworkEvent {
    }

    public static final class LoggingOut extends ClientPlayerNetworkEvent {
    }
}
