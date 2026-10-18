package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.crafting.DraftingTableRecipes;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The recipes an open drafting table lists, sent by the server once its menu opens. */
public record DraftingTableRecipesPayload(int menuId, List<DraftingTableRecipes.Entry> recipes)
        implements CustomPacketPayload {

    public static final Type<DraftingTableRecipesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "drafting_table_recipes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DraftingTableRecipesPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DraftingTableRecipesPayload::menuId,
                    DraftingTableRecipes.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    DraftingTableRecipesPayload::recipes,
                    DraftingTableRecipesPayload::new);

    @Override
    public Type<DraftingTableRecipesPayload> type() {
        return TYPE;
    }
}
