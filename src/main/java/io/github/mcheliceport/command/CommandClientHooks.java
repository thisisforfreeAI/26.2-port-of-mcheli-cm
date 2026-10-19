package io.github.mcheliceport.command;

import io.github.mcheliceport.net.TitlePayload;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** What {@code /mcheli} asks of the client. The client registers it; on a server nothing is registered. */
public final class CommandClientHooks {

    private static volatile @Nullable Consumer<TitlePayload> title;

    private CommandClientHooks() {
    }

    public static void setTitle(@Nullable Consumer<TitlePayload> handler) {
        title = handler;
    }

    /** Upstream {@code PacketTitle#onReceive}: shows the {@code /mcheli title} message. */
    public static void showTitle(TitlePayload payload) {
        Consumer<TitlePayload> handler = title;
        if (handler != null) {
            handler.accept(payload);
        }
    }
}
