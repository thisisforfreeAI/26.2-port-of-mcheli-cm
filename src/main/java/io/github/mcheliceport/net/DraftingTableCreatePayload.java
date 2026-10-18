package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The drafting table's Create button (upstream {@code PacketDrafttingTableCreate}): make this
 * recipe into the output slot of the open drafting table menu.
 */
public record DraftingTableCreatePayload(int menuId, Identifier recipe) implements CustomPacketPayload {

    public static final Type<DraftingTableCreatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "drafting_table_create"));

    public static final StreamCodec<FriendlyByteBuf, DraftingTableCreatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DraftingTableCreatePayload::menuId,
            Identifier.STREAM_CODEC, DraftingTableCreatePayload::recipe,
            DraftingTableCreatePayload::new);

    @Override
    public Type<DraftingTableCreatePayload> type() {
        return TYPE;
    }
}
