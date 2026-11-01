package io.github.mcheliceport.mixin;

import io.github.mcheliceport.shim.PartEntities;
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

/** Entity searches see the port's hit box parts, as NeoForge's searches see its own part entities. */
@Mixin(Level.class)
public abstract class LevelPartsMixin {

    @Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At("RETURN"))
    private void mcheli$addParts(@Nullable Entity except, AABB area, Predicate<? super Entity> filter,
            CallbackInfoReturnable<List<Entity>> cir) {
        PartEntities.addParts((Level) (Object) this, cir.getReturnValue(), except, area, filter);
    }

    @Inject(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V",
            at = @At("TAIL"))
    private <T extends Entity> void mcheli$addTypedParts(EntityTypeTest<Entity, T> test, AABB area,
            Predicate<? super T> filter, List<? super T> found, int limit, CallbackInfo ci) {
        PartEntities.addParts((Level) (Object) this, test, area, filter, found, limit);
    }
}
