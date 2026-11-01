package io.github.mcheliceport.shim.client.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a static {@link ClientBus} listener: one parameter, a {@link ClientEvent}. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubscribeEvent {
    /** Accepted for source compatibility with NeoForge; listeners run in registration order. */
    int priority() default 0;

    boolean receiveCanceled() default false;
}
