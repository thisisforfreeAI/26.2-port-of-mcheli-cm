package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.model.BulletModels;
import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.client.tool.RangeFinderClient;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.item.LightWeaponItem;
import io.github.mcheliceport.item.WrenchItem;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * The 3D models of the hand tools in the hand and on the ground: upstream's built-in item
 * renderers ({@code BuiltInLightWeaponItemRenderer}, {@code BuiltInGLTDItemRenderer},
 * {@code BuiltInWrenchItemRenderer} and {@code BuiltInRangeFinderItemRenderer}). Everywhere else
 * (the inventory, frames, the head) the item keeps its flat model, the {@code fallback}.
 *
 * <p>As upstream's {@code MCH_BakedModel}, the base model's display transforms place the model,
 * which is then drawn from the corner of the item's unit cube.
 */
public final class ToolItemModel implements ItemModel {

    public static final Identifier TYPE = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "tool");

    /** What each tool draws and when. */
    public enum Tool implements StringRepresentable {
        FIM92("fim92", "lweapons/fim92", "textures/lweapon/fim92.png", false),
        FGM148("fgm148", "lweapons/fgm148", "textures/lweapon/fgm148.png", false),
        GLTD("gltd", "gltd", "textures/gltd.png", true),
        WRENCH("wrench", "wrench", "textures/wrench.png", false),
        RANGEFINDER("rangefinder", "rangefinder", "textures/rangefinder.png", true);

        public static final Codec<Tool> CODEC = StringRepresentable.fromEnum(Tool::values);

        private final String name;
        private final String model;
        private final Identifier texture;
        private final boolean onGround;

        Tool(String name, String model, String texture, boolean onGround) {
            this.name = name;
            this.model = model;
            this.texture = Identifier.fromNamespaceAndPath("mcheli", texture);
            this.onGround = onGround;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        /** Upstream {@code shouldRenderer}: in either hand, and on the ground for some. */
        boolean drawsIn(ItemDisplayContext context) {
            return context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                    || this.onGround && context == ItemDisplayContext.GROUND;
        }

        boolean isLightWeapon() {
            return this == FIM92 || this == FGM148;
        }
    }

    /** How one frame draws a tool: in first person, held up in use, and the wrench's swing. */
    public record Pose(boolean firstPersonInUse, float wrenchSwing) {
    }

    private final Tool tool;
    private final ItemModel fallback;
    private final ModelRenderProperties properties;
    private final Matrix4fc transformation;
    private final Renderer renderer;

    private ToolItemModel(Tool tool, ItemModel fallback, ModelRenderProperties properties, Matrix4fc transformation) {
        this.tool = tool;
        this.fallback = fallback;
        this.properties = properties;
        this.transformation = transformation;
        this.renderer = new Renderer(tool);
    }

    public static void register(RegisterItemModelsEvent event) {
        event.register(TYPE, Unbaked.MAP_CODEC);
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver,
            ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        if (!this.tool.drawsIn(displayContext)) {
            this.fallback.update(output, item, resolver, displayContext, level, owner, seed);
            return;
        }
        output.appendModelIdentityElement(this);
        LivingEntity entity = owner != null ? owner.asLivingEntity() : null;
        boolean firstPerson = displayContext.firstPerson();
        if (firstPerson && hiddenInFirstPerson(entity)) {
            return;
        }
        boolean inUse = firstPerson && entity != null && entity.isUsingItem()
                && entity.getUsedItemHand() == InteractionHand.MAIN_HAND;
        float swing = this.tool == Tool.WRENCH && inUse ? WrenchSwing.smooth() : 0.0F;
        Pose pose = new Pose(inUse, swing);
        ItemStackRenderState.LayerRenderState layer = output.newLayer();
        if (item.hasFoil()) {
            layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
            output.setAnimated();
            output.appendModelIdentityElement(ItemStackRenderState.FoilType.STANDARD);
        }
        layer.setExtents(this::extents);
        layer.setLocalTransform(this.transformation);
        layer.setupSpecialModel(this.renderer, pose);
        output.appendModelIdentityElement(pose);
        if (this.tool == Tool.WRENCH && inUse) {
            output.setAnimated();
        }
        this.properties.applyToLayer(layer, displayContext);
    }

    private Vector3fc[] extents() {
        java.util.List<Vector3fc> points = new java.util.ArrayList<>(2);
        this.renderer.getExtents(points::add);
        return points.toArray(new Vector3fc[0]);
    }

    /**
     * Upstream: aiming a launcher in first person hides it (the sight covers the view), and so does
     * looking through the rangefinder's scope.
     */
    private boolean hiddenInFirstPerson(@Nullable LivingEntity entity) {
        if (this.tool.isLightWeapon()) {
            return entity instanceof Player player && LightWeaponItem.isHeld(player)
                    && entity == Minecraft.getInstance().player;
        }
        if (this.tool == Tool.RANGEFINDER) {
            return !(entity instanceof Player player) || RangeFinderClient.isUsingScope(player);
        }
        return false;
    }

    /** Draws the tool's model with upstream's offsets. */
    private static final class Renderer implements SpecialModelRenderer<Pose> {

        private final Tool tool;

        Renderer(Tool tool) {
            this.tool = tool;
        }

        @Override
        public void submit(@Nullable Pose pose, PoseStack poseStack, SubmitNodeCollector collector, int light,
                int overlay, boolean hasFoil, int outlineColor) {
            McheliModel model = BulletModels.get(this.tool.model);
            if (model == null) {
                return;
            }
            poseStack.pushPose();
            if (pose != null && pose.firstPersonInUse()) {
                switch (this.tool) {
                    case FIM92, FGM148 -> poseStack.translate(0.13F, 0.27F, 0.01F);
                    case RANGEFINDER -> poseStack.translate(0.6563F, 0.3438F, 0.01F);
                    case WRENCH -> {
                        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(65.0F));
                        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pose.wrenchSwing() + 20.0F));
                    }
                    default -> {
                    }
                }
            }
            // Upstream drew the launchers flat unless smooth shading was on; the rest kept GL's smooth default.
            boolean flat = this.tool.isLightWeapon() && !ClientConfig.get(ClientConfig.SMOOTH_SHADING);
            var groups = model.groups;
            collector.submitCustomGeometry(poseStack, AircraftRenderTypes.aircraft(this.tool.texture),
                    (p, buffer) -> ModelVertices.emit(buffer, p, groups, 0xFFFFFFFF, light, flat));
            poseStack.popPose();
        }

        @Override
        public void getExtents(Consumer<Vector3fc> output) {
            McheliModel model = BulletModels.get(this.tool.model);
            if (model != null) {
                output.accept(new org.joml.Vector3f(model.minX, model.minY, model.minZ));
                output.accept(new org.joml.Vector3f(model.maxX, model.maxY, model.maxZ));
            }
        }

        @Override
        public @Nullable Pose extractArgument(ItemStack stack) {
            return null;
        }
    }

    /**
     * Upstream {@code MCH_ItemWrench#getUseAnimSmooth}: while the local player repairs a vehicle,
     * a counter runs from 16 down to 0 and round again, and the wrench swings by its distance from 8.
     * Upstream kept the counter in the client's copy of the item; here it is the client's own.
     */
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
            float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            int i = Math.abs(count - 8);
            int j = Math.abs(prev - 8);
            return j + (i - j) * partial;
        }
    }

    public record Unbaked(Tool tool, Identifier base, ItemModel.Unbaked fallback) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Tool.CODEC.fieldOf("tool").forGetter(Unbaked::tool),
                Identifier.CODEC.fieldOf("base").forGetter(Unbaked::base),
                ItemModels.CODEC.fieldOf("fallback").forGetter(Unbaked::fallback))
                .apply(i, Unbaked::new));

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(this.base);
            this.fallback.resolveDependencies(resolver);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            ModelBaker baker = context.blockModelBaker();
            ResolvedModel model = baker.getModel(this.base);
            ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, model,
                    model.getTopTextureSlots());
            return new ToolItemModel(this.tool, this.fallback.bake(context, transformation), properties,
                    Transformation.compose(transformation, java.util.Optional.empty()));
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
