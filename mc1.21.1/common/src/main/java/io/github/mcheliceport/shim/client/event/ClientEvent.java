package io.github.mcheliceport.shim.client.event;

/** Base of the client event stand-ins that {@link ClientBus} delivers. */
public abstract class ClientEvent {
    private boolean canceled;

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}
