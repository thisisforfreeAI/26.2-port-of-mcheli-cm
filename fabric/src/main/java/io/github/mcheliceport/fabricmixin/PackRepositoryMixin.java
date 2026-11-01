package io.github.mcheliceport.fabricmixin;

import io.github.mcheliceport.platform.GameEvents;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.FolderRepositorySource;
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
 * NeoForge's {@code AddPackFindersEvent}: a pack repository built on a vanilla built-in source
 * (server data or client resources) gets the mod's sources too. Trusted = the client's vanilla-only
 * data repository, which has no pack folder.
 */
@Mixin(PackRepository.class)
abstract class PackRepositoryMixin {
    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mcheli$addPackFinders(RepositorySource[] given, CallbackInfo ci) {
        PackType type = null;
        boolean trusted = true;
        for (RepositorySource source : sources) {
            if (source instanceof BuiltInPackSource builtIn) {
                type = ((BuiltInPackSourceAccessor) builtIn).mcheli$packType();
            } else if (source instanceof FolderRepositorySource) {
                trusted = false;
            }
        }
        if (type == null) {
            return;
        }
        Set<RepositorySource> all = new LinkedHashSet<>(sources);
        GameEvents.addPackFinders(type, trusted, all::add);
        sources = all;
    }
}
