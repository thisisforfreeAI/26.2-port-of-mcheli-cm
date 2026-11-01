package io.github.mcheliceport.fabricmixin;

import io.github.mcheliceport.platform.fabric.FabricParts;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vehicle hit boxes in entity lookups, as NeoForge's patched {@code Level} does for part entities. */
@Mixin(Level.class)
abstract class LevelPartsMixin {

    @Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At("RETURN"))
    private void mcheli$addParts(@Nullable Entity except, AABB box, Predicate<? super Entity> selector,
            CallbackInfoReturnable<List<Entity>> cir) {
        List<Entity> output = cir.getReturnValue();
        FabricParts.forEachPart((Level) (Object) this, part -> {
            if (part != except && part.getParent() != except && selector.test(part)
                    && box.intersects(part.getBoundingBox())) {
                output.add(part);
            }
        });
    }

    @Inject(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V",
            at = @At("TAIL"))
    private <T extends Entity> void mcheli$addTypedParts(EntityTypeTest<Entity, T> type, AABB box,
            Predicate<? super T> selector, List<? super T> output, int maxResults, CallbackInfo ci) {
        if (output.size() >= maxResults) {
            return;
        }
        FabricParts.forEachPart((Level) (Object) this, part -> {
            T cast = type.tryCast(part);
            if (cast != null && output.size() < maxResults && cast.getBoundingBox().intersects(box)
                    && selector.test(cast)) {
                output.add(cast);
            }
        });
    }

    @Inject(method = "hasEntities", at = @At("RETURN"), cancellable = true)
    private <T extends Entity> void mcheli$hasParts(EntityTypeTest<Entity, T> type, AABB box,
            Predicate<? super T> selector, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        boolean[] found = {false};
        FabricParts.forEachPart((Level) (Object) this, part -> {
            T cast = type.tryCast(part);
            if (!found[0] && cast != null && cast.getBoundingBox().intersects(box) && selector.test(cast)) {
                found[0] = true;
            }
        });
        if (found[0]) {
            cir.setReturnValue(true);
        }
    }
}
