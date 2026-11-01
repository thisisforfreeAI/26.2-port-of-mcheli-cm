package io.github.mcheliceport.shim.client.event;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The client's event bus: one bus for what NeoForge splits into the mod and game buses. The loader
 * modules post the stand-in events from their own hooks.
 */
public final class ClientBus {

    private record Listener(Class<?> type, Consumer<Object> handler) {
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private ClientBus() {
    }

    @SuppressWarnings("unchecked")
    public static <E extends ClientEvent> void addListener(Class<E> type, Consumer<E> handler) {
        LISTENERS.add(new Listener(type, e -> handler.accept((E) e)));
    }

    /** NeoForge's {@code addListener(Owner::method)}: the static one-argument {@code method} of {@code owner}. */
    public static void listen(Class<?> owner, String method) {
        boolean found = false;
        for (Method m : owner.getDeclaredMethods()) {
            if (m.getName().equals(method) && Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                    && ClientEvent.class.isAssignableFrom(m.getParameterTypes()[0])) {
                add(m);
                found = true;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("No static event listener " + owner.getName() + "#" + method);
        }
    }

    /** NeoForge's {@code @EventBusSubscriber}: every static {@link SubscribeEvent} method of {@code owner}. */
    public static void subscribe(Class<?> owner) {
        for (Method m : owner.getDeclaredMethods()) {
            if (m.isAnnotationPresent(SubscribeEvent.class) && Modifier.isStatic(m.getModifiers())
                    && m.getParameterCount() == 1) {
                add(m);
            }
        }
    }

    private static void add(Method m) {
        m.setAccessible(true);
        Class<?> type = m.getParameterTypes()[0];
        LISTENERS.add(new Listener(type, e -> {
            try {
                m.invoke(null, e);
            } catch (ReflectiveOperationException ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException(cause);
            }
        }));
    }

    /** Delivers {@code event} to its listeners; returns it, so callers can read what they set. */
    public static <E extends ClientEvent> E post(E event) {
        for (Listener l : LISTENERS) {
            if (l.type().isInstance(event)) {
                l.handler().accept(event);
            }
        }
        return event;
    }

    public static boolean hasListeners(Class<? extends ClientEvent> type) {
        for (Listener l : LISTENERS) {
            if (l.type().isAssignableFrom(type)) {
                return true;
            }
        }
        return false;
    }
}
