package io.github.mcheliceport.fabric.mixin;

import io.github.mcheliceport.fabric.FabricPlatform;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's AddPackFindersEvent: the port's pack sources join every repository that has a pack
 * folder (the resource pack list, and a world's untrusted data pack list).
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {

    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mcheli$addSources(RepositorySource[] given, CallbackInfo ci) {
        PackType type = null;
        for (RepositorySource source : given) {
            if (source instanceof FolderRepositorySource folder) {
                type = ((FolderRepositorySourceAccessor) folder).mcheli$packType();
            }
        }
        if (type == null) {
            return;
        }
        // Read the sources when packs are discovered, not now: the client's repository is built
        // before the mod initializers that register them have run.
        PackType packType = type;
        Set<RepositorySource> all = new LinkedHashSet<>(this.sources);
        all.add(packs -> {
            List<Consumer<Consumer<Pack>>> extra = FabricPlatform.PACK_SOURCES.get(packType);
            if (extra != null) {
                for (Consumer<Consumer<Pack>> source : extra) {
                    source.accept(packs);
                }
            }
        });
        this.sources = Set.copyOf(all);
    }
}
