package io.github.mcheliceport.effectrange;

import io.github.mcheliceport.net.EffectsRangeNoticePayload;
import java.util.function.Consumer;

/** Dedicated servers never resolve a client class. */
public final class EffectsRangeClientHooks {
    private static Consumer<EffectsRangeNoticePayload> notice=p->{};
    private EffectsRangeClientHooks(){}
    public static void register(Consumer<EffectsRangeNoticePayload> handler){notice=handler;}
    public static void notice(EffectsRangeNoticePayload payload){notice.accept(payload);}
}
