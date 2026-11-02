package io.github.mcheliceport.platform;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;

/**
 * The registrars that {@link ClientEvents}' registration events hand their listeners. The method
 * names are NeoForge's, so a listener reads as it did on NeoForge's registration events; each
 * loader backs them with its own registries.
 */
public final class ClientRegistrars {

    private ClientRegistrars() {
    }

    /** Render pipelines to compile up front (NeoForge's {@code RegisterRenderPipelinesEvent}). */
    public interface Pipelines {
        void registerPipeline(RenderPipeline pipeline);
    }

    /** NeoForge's {@code RegisterParticleGroupsEvent}. */
    public interface ParticleGroups {
        void register(ParticleRenderType type, Function<ParticleEngine, ParticleGroup<?>> factory);
    }

    /** NeoForge's {@code RegisterKeyMappingsEvent}. */
    public interface KeyMappings {
        void registerCategory(KeyMapping.Category category);

        void register(KeyMapping key);
    }

    /** NeoForge's {@code EntityRenderersEvent.RegisterRenderers}. */
    public interface Renderers {
        <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider);

        <T extends BlockEntity, S extends BlockEntityRenderState> void registerBlockEntityRenderer(
                BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider);
    }

    /** NeoForge's {@code EntityRenderersEvent.RegisterLayerDefinitions}. */
    public interface LayerDefinitions {
        void registerLayerDefinition(ModelLayerLocation layer, Supplier<LayerDefinition> definition);
    }

    /** NeoForge's {@code RegisterMenuScreensEvent}. */
    public interface MenuScreenRegistrar {
        <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M> type,
                MenuScreens.ScreenConstructor<M, U> constructor);
    }

    /** NeoForge's {@code RegisterItemModelsEvent}. */
    public interface ItemModels {
        void register(Identifier id, MapCodec<? extends ItemModel.Unbaked> codec);
    }

    /** NeoForge's {@code RegisterPictureInPictureRenderersEvent}. */
    public interface PictureInPicture {
        <T extends PictureInPictureRenderState> void register(Class<T> stateClass,
                Supplier<PictureInPictureRenderer<T>> factory);
    }

    /** Client resource reload listeners (NeoForge's {@code AddClientReloadListenersEvent}). */
    public interface ReloadListeners {
        void addListener(Identifier id, PreparableReloadListener listener);
    }

    /** Feature renderers for custom submit nodes (NeoForge's {@code RegisterFeatureRenderersEvent}). */
    public interface FeatureRenderers {
        <S extends SubmitNode> void register(FeatureRendererType<S> type, Supplier<FeatureRenderer<S>> renderer);
    }

    /** Fluids drawn with two textures and one tint (NeoForge's {@code RegisterFluidModelsEvent}). */
    public interface FluidModels {
        void register(Identifier stillTexture, Identifier flowingTexture, int tint, Fluid still, Fluid flowing);
    }
}
