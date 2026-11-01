package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.mcheliceport.block.DraftingTableBlock;
import io.github.mcheliceport.block.DraftingTableBlockEntity;
import io.github.mcheliceport.client.model.BulletModels;
import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.shim.client.SubmitNodeCollector;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the drafting table's 3D model (upstream {@code MCH_DraftingTableRenderer}): centred on the
 * block, turned by its eight-way direction and tinted 75% grey, as upstream.
 */
public class DraftingTableRenderer implements BlockEntityRenderer<DraftingTableBlockEntity> {

    public static final String MODEL = "blocks/drafting_table";
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("mcheli", "textures/blocks/drafting_table.png");
    /** Upstream's {@code GlStateManager.color(0.75, 0.75, 0.75, 1)}. */
    private static final int COLOR = 0xFFBFBFBF;

    public DraftingTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DraftingTableBlockEntity blockEntity, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffers, int light, int overlay) {
        var blockState = blockEntity.getBlockState();
        int index = blockState.hasProperty(DraftingTableBlock.DIRECTION8)
                ? blockState.getValue(DraftingTableBlock.DIRECTION8).index() : 0;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-index * 45.0F + 180.0F));
        submitModel(poseStack, new SubmitNodeCollector(buffers), COLOR, light);
        poseStack.popPose();
    }

    /** Draws the model at the pose's origin; shared with the item ({@code ToolItemModel}). */
    static void submitModel(PoseStack poseStack, SubmitNodeCollector collector, int color, int light) {
        McheliModel model = BulletModels.get(MODEL);
        if (model == null) {
            return;
        }
        boolean flat = !ClientConfig.get(ClientConfig.SMOOTH_SHADING);
        var groups = model.groups;
        collector.submitCustomGeometry(poseStack, AircraftRenderTypes.aircraft(TEXTURE),
                (pose, buffer) -> ModelVertices.emit(buffer, pose, groups, color, light, flat));
    }
}
