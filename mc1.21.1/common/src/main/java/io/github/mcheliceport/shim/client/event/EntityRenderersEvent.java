package io.github.mcheliceport.shim.client.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Collects the port's renderers and model layers for the loader to register. */
public abstract class EntityRenderersEvent extends ClientEvent {

    public record EntityEntry<T extends Entity>(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
    }

    public record BlockEntityEntry<T extends BlockEntity>(BlockEntityType<? extends T> type,
            BlockEntityRendererProvider<T> provider) {
    }

    public static final class RegisterRenderers extends EntityRenderersEvent {
        public final List<EntityEntry<?>> entities = new ArrayList<>();
        public final List<BlockEntityEntry<?>> blockEntities = new ArrayList<>();

        public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type,
                EntityRendererProvider<T> provider) {
            entities.add(new EntityEntry<>(type, provider));
        }

        public <T extends BlockEntity> void registerBlockEntityRenderer(BlockEntityType<? extends T> type,
                BlockEntityRendererProvider<T> provider) {
            blockEntities.add(new BlockEntityEntry<>(type, provider));
        }
    }

    public record LayerEntry(ModelLayerLocation location, Supplier<LayerDefinition> definition) {
    }

    public static final class RegisterLayerDefinitions extends EntityRenderersEvent {
        public final List<LayerEntry> layers = new ArrayList<>();

        public void registerLayerDefinition(ModelLayerLocation location, Supplier<LayerDefinition> definition) {
            layers.add(new LayerEntry(location, definition));
        }
    }
}
