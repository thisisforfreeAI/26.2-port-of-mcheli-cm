package io.github.mcheliceport.platform.network;

import net.minecraft.world.entity.player.Player;

/** What a payload handler gets: NeoForge's {@code IPayloadContext}. Handlers run on the game thread. */
public interface IPayloadContext {

    /** The sending player on the server, the local player on the client. */
    Player player();

    /** Runs {@code task} on the game thread (handlers already run there; kept for code from 26.2). */
    void enqueueWork(Runnable task);
}
