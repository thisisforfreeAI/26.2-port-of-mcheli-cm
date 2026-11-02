package io.github.mcheliceport.platform.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A list of listeners that the loader modules fire; see {@link ModEvents}. */
public final class Event<L> {
    private final List<L> listeners = new CopyOnWriteArrayList<>();

    public void register(L listener) {
        listeners.add(listener);
    }

    /** The listeners in registration order, for the loader module that fires the event. */
    public List<L> listeners() {
        return listeners;
    }

    public boolean isEmpty() {
        return listeners.isEmpty();
    }
}
