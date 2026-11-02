package io.github.mcheliceport.shim.client.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * NeoForge's {@code @EventBusSubscriber} for the client: classes with it are listed in
 * {@code client.ClientSubscribers} (kept by {@code scripts/backport_client.py}) and handed to
 * {@link ClientBus#subscribe}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ClientSubscriber {
}
