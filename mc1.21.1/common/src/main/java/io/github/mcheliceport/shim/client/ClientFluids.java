package io.github.mcheliceport.shim.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

/**
 * 26.2's {@code RegisterFluidModelsEvent}, as data: each fluid's still and flowing sprites and
 * tint. NeoForge hands these to its client fluid type extensions, Fabric to its fluid render
 * handler registry.
 */
public final class ClientFluids {
    public record Entry(ResourceLocation still, ResourceLocation flowing, int tint, List<Supplier<? extends Fluid>> fluids) {}

    public static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    private ClientFluids() {}

    @SafeVarargs
    public static void register(ResourceLocation still, ResourceLocation flowing, int tint,
            Supplier<? extends Fluid>... fluids) {
        ENTRIES.add(new Entry(still, flowing, tint, List.of(fluids)));
    }
}
