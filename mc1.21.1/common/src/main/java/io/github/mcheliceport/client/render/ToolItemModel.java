package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.model.BulletModels;
import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.client.tool.RangeFinderClient;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.item.LightWeaponItem;
import io.github.mcheliceport.item.WrenchItem;
import io.github.mcheliceport.shim.client.SubmitNodeCollector;
import java.io.Reader;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The 3D models of the hand tools in the hand and on the ground: upstream's built-in item
 * renderers ({@code BuiltInLightWeaponItemRenderer}, {@code BuiltInGLTDItemRenderer},
 * {@code BuiltInWrenchItemRenderer}, {@code BuiltInRangeFinderItemRenderer} and
 * {@code BuiltInDraftingTableItemRenderer}). Everywhere else (the inventory, frames, the head) the
 * hand tools keep their flat item model; the drafting table is drawn everywhere.
 *
 * <p>26.2 does this with a custom item model type. 1.21.1 has none, so the item renderer is
 * intercepted ({@code ItemRendererMixin}): the display transforms of the tool's
 * {@code item/<tool>_hand} model place the model, which is then drawn from the corner of the
 * item's unit cube, as upstream's {@code MCH_BakedModel}.
 */
public final class ToolItemModel {

    /** What each tool draws and when. */
    public enum Tool {
        FIM92("fim92", "lweapons/fim92", "textures/lweapon/fim92.png", false),
        FGM148("fgm148", "lweapons/fgm148", "textures/lweapon/fgm148.png", false),
        GLTD("gltd", "gltd", "textures/gltd.png", true),
        WRENCH("wrench", "wrench", "textures/wrench.png", false),
        RANGEFINDER("rangefinder", "rangefinder", "textures/rangefinder.png", true),
        /** Upstream {@code BuiltInDraftingTableItemRenderer}: drawn everywhere, the inventory included. */
        DRAFTING_TABLE("drafting_table", DraftingTableRenderer.MODEL, "textures/blocks/drafting_table.png", true);

        private final String name;
        private final String model;
        private final ResourceLocation texture;
        private final boolean onGround;

        Tool(String name, String model, String texture, boolean onGround) {
            this.name = name;
            this.model = model;
            this.texture = ResourceLocation.fromNamespaceAndPath("mcheli", texture);
            this.onGround = onGround;
        }

        /** Upstream {@code shouldRenderer}: in either hand, and on the ground for some. */
        boolean drawsIn(ItemDisplayContext context) {
            return this == DRAFTING_TABLE || context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                    || this.onGround && context == ItemDisplayContext.GROUND;
        }

        boolean isLightWeapon() {
            return this == FIM92 || this == FGM148;
        }

        static @Nullable Tool of(ItemStack stack) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!McheliCePort.MOD_ID.equals(id.getNamespace())) {
                return null;
            }
            for (Tool tool : values()) {
                if (tool.name.equals(id.getPath())) {
                    return tool;
                }
            }
            return null;
        }
    }

    private static final Map<Tool, ItemTransforms> TRANSFORMS = new EnumMap<>(Tool.class);

    private ToolItemModel() {
    }

    /** Resource reload: the hand models are read again on first use. */
    public static void clear() {
        synchronized (TRANSFORMS) {
            TRANSFORMS.clear();
        }
    }

    private static ItemTransforms transforms(Tool tool) {
        synchronized (TRANSFORMS) {
            return TRANSFORMS.computeIfAbsent(tool, t -> {
                ResourceManager manager = Minecraft.getInstance().getResourceManager();
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(McheliCePort.MOD_ID,
                        "models/item/" + t.name + "_hand.json");
                try (Reader reader = manager.openAsReader(id)) {
                    return BlockModel.fromStream(reader).getTransforms();
                } catch (Exception e) {
                    McheliCePort.LOGGER.warn("Could not read the hand model {}", id, e);
                    return ItemTransforms.NO_TRANSFORMS;
                }
            });
        }
    }

    /**
     * Draws a tool's 3D model in place of its item model.
     *
     * @return whether the item was drawn here (or hidden), so the item renderer should stop
     */
    public static boolean render(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poseStack,
            MultiBufferSource buffers, int light, @Nullable LivingEntity entity) {
        Tool tool = Tool.of(stack);
        if (tool == null || !tool.drawsIn(context)) {
            return false;
        }
        boolean firstPerson = context.firstPerson();
        if (firstPerson && hiddenInFirstPerson(tool, entity)) {
            return true;
        }
        McheliModel model = BulletModels.get(tool.model);
        if (model == null) {
            return false;
        }
        boolean inUse = firstPerson && entity != null && entity.isUsingItem()
                && entity.getUsedItemHand() == InteractionHand.MAIN_HAND;
        poseStack.pushPose();
        transforms(tool).getTransform(context).apply(leftHand, poseStack);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        if (tool == Tool.DRAFTING_TABLE) {
            draftingTablePose(context, poseStack);
            DraftingTableRenderer.submitModel(poseStack, new SubmitNodeCollector(buffers), 0xFFFFFFFF, light);
            poseStack.popPose();
            return true;
        }
        if (inUse) {
            switch (tool) {
                case FIM92, FGM148 -> poseStack.translate(0.13F, 0.27F, 0.01F);
                case RANGEFINDER -> poseStack.translate(0.6563F, 0.3438F, 0.01F);
                case WRENCH -> {
                    poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(65.0F));
                    poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(WrenchSwing.smooth() + 20.0F));
                }
                default -> {
                }
            }
        }
        // Upstream drew the launchers flat unless smooth shading was on; the rest kept GL's smooth default.
        boolean flat = tool.isLightWeapon() && !ClientConfig.get(ClientConfig.SMOOTH_SHADING);
        var groups = model.groups;
        new SubmitNodeCollector(buffers).submitCustomGeometry(poseStack, AircraftRenderTypes.aircraft(tool.texture),
                (p, buffer) -> ModelVertices.emit(buffer, p, groups, 0xFFFFFFFF, light, flat));
        poseStack.popPose();
        return true;
    }

    /**
     * Upstream: aiming a launcher in first person hides it (the sight covers the view), and so does
     * looking through the rangefinder's scope.
     */
    private static boolean hiddenInFirstPerson(Tool tool, @Nullable LivingEntity entity) {
        if (tool.isLightWeapon()) {
            return entity instanceof Player player && LightWeaponItem.isHeld(player)
                    && entity == Minecraft.getInstance().player;
        }
        if (tool == Tool.RANGEFINDER) {
            return !(entity instanceof Player player) || RangeFinderClient.isUsingScope(player);
        }
        return false;
    }

    /** Upstream {@code BuiltInDraftingTableItemRenderer#renderItem}'s offsets for each place. */
    private static void draftingTablePose(ItemDisplayContext context, PoseStack poseStack) {
        switch (context) {
            case GROUND -> {
                poseStack.translate(0.0F, 0.5F, 0.0F);
                poseStack.scale(1.5F, 1.5F, 1.5F);
            }
            case GUI, FIXED -> {
                poseStack.translate(0.0F, -0.5F, 0.0F);
                poseStack.scale(0.75F, 0.75F, 0.75F);
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> poseStack.translate(0.0F, 0.0F, 0.5F);
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                poseStack.translate(0.75F, 0.0F, 0.0F);
                poseStack.mulPose(com.mojang.math.Axis.YN.rotationDegrees(90.0F));
            }
            default -> {
            }
        }
    }

    public static final class WrenchSwing {
        private static int count;
        private static int prev;

        private WrenchSwing() {
        }

        /** Client tick: upstream {@code onUsingTick} and {@code onPlayerStoppedUsing}. */
        public static void tick() {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || !player.isUsingItem() || !(player.getUseItem().getItem() instanceof WrenchItem)) {
                count = 0;
                prev = 0;
                return;
            }
            if (WrenchItem.aimedAircraft(player) != null) {
                prev = count;
                count = count <= 0 ? 16 : count - 1;
            }
        }

        static float smooth() {
            float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            int i = Math.abs(count - 8);
            int j = Math.abs(prev - 8);
            return j + (i - j) * partial;
        }
    }
}
