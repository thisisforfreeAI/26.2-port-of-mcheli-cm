package io.github.mcheliceport.platform.neoforge;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.platform.Payloads;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Hooks {@link ModNetwork} to NeoForge's payload registration and events. */
public final class NeoForgeNetwork {

    private NeoForgeNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> ModNetwork.registerPayloads(
                new Registrar(event.registrar(ModNetwork.VERSION), event.registrar(ModNetwork.VERSION).optional())));
        modBus.addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == ServerConfig.SPEC && event.getConfig().getType() == ModConfig.Type.SERVER) {
                ModNetwork.onServerConfigReloaded(ServerLifecycleHooks.getCurrentServer());
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ModNetwork.onPlayerLoggedIn(player);
            }
        });
    }

    private record Registrar(PayloadRegistrar required, PayloadRegistrar optional) implements Payloads {
        @Override
        public <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type,
                StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Handler<T> handler) {
            required.playToServer(type, codec, (payload, context) -> handler.handle(payload, context::player));
        }

        @Override
        public <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type,
                StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Handler<T> handler) {
            required.playToClient(type, codec, (payload, context) -> handler.handle(payload, context::player));
        }

        @Override
        public <T extends CustomPacketPayload> void optionalPlayToClient(CustomPacketPayload.Type<T> type,
                StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Handler<T> handler) {
            optional.playToClient(type, codec, (payload, context) -> handler.handle(payload, context::player));
        }
    }
}
