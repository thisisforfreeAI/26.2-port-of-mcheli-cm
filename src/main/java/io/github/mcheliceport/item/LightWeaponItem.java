package io.github.mcheliceport.item;

import io.github.mcheliceport.config.ServerSettings;
import io.github.mcheliceport.net.LightWeaponControlPayload;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponCreator;
import io.github.mcheliceport.weapon.WeaponParam;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The hand-held FIM-92 Stinger and FGM-148 Javelin (upstream {@code MCH_ItemLightWeaponBase} and
 * {@code PacketPlayerLightWeaponControl}). Holding use raises the sight; the client locks on and
 * asks the server to fire ({@link #handleControl}). A shot empties the launcher (its damage goes
 * to the maximum) and a missile item in the inventory reloads it.
 *
 * <p>The sight, lock, zoom, modes and reload timing are client-side ({@code LightWeaponClient}).
 */
public class LightWeaponItem extends Item {

    /** Upstream {@code isHeld}: used for more than this many ticks. */
    public static final int AIM_TICKS = 10;
    /** Night vision can be switched on after this many ticks of aiming. */
    public static final int NIGHT_VISION_TICKS = 60;
    /** Upstream's night vision lasts 255 ticks and is topped up to 250 while aiming. */
    private static final int NIGHT_VISION_DURATION = 255;
    /** A locked target may move a little past the lock range before the shot reaches the server. */
    private static final double TARGET_RANGE_SLACK = 1.5;

    private final Supplier<? extends Item> bullet;

    public LightWeaponItem(Properties properties, Supplier<? extends Item> bullet) {
        super(properties);
        this.bullet = bullet;
    }

    public Item bullet() {
        return this.bullet.get();
    }

    /** The weapon definition's name, the item's own ({@code fim92} or {@code fgm148}). */
    public static String weaponName(ItemStack stack) {
        return stack.getItem() instanceof LightWeaponItem ? BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()
                : "";
    }

    /** Upstream {@code isHeld}: a light weapon in the main hand, aimed for more than ten ticks. */
    public static boolean isHeld(@Nullable Player player) {
        return player != null && player.getMainHandItem().getItem() instanceof LightWeaponItem
                && player.getTicksUsingItem() > AIM_TICKS;
    }

    /** Whether the launcher holds a missile. */
    public static boolean isLoaded(ItemStack stack) {
        return stack.getDamageValue() < stack.getMaxDamage();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    /** Upstream {@code onUsingTick}: night vision switched on while aiming does not run out. */
    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        MobEffectInstance effect = user.getEffect(MobEffects.NIGHT_VISION);
        if (effect != null && effect.getDuration() < 220) {
            user.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 250, 0, false, false));
        }
    }

    /** Upstream {@code onEntitySwing}: the launcher is not swung. */
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        return true;
    }

    /**
     * Upstream {@code PacketPlayerLightWeaponControl#onReceive}: switches night vision (off at
     * once; on only while aiming), fires at the locked target or reloads with a missile item.
     *
     * <p>Upstream fired from the position the client sent and took the target on trust; here the
     * shot leaves the player's own eyes, needs the player to be aiming, and the target must be
     * alive and within the lock range.
     */
    public static void handleControl(ServerPlayer player, LightWeaponControlPayload p) {
        if (p.cameraMode() == LightWeaponControlPayload.NIGHT_VISION_OFF) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        } else if (p.cameraMode() == LightWeaponControlPayload.NIGHT_VISION_ON && isHeld(player)) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_DURATION, 0, false, false));
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof LightWeaponItem weapon)) {
            return;
        }
        if (p.useWeapon() && isLoaded(held)) {
            fire(player, held, p.targetId(), p.weaponMode());
            return;
        }
        if (p.reload() && held.getDamageValue() > 1 && consumeBullet(player, weapon.bullet())) {
            held.setDamageValue(0);
        }
    }

    private static void fire(ServerPlayer player, ItemStack held, int targetId, int mode) {
        if (!player.isUsingItem() || player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Entity target = player.level().getEntity(targetId);
        double range = ServerSettings.server().stingerLockRange() * TARGET_RANGE_SLACK;
        if (target == null || !target.isAlive() || target == player || target.distanceToSqr(player) > range * range) {
            return;
        }
        ServerLevel level = player.level();
        WeaponBase weapon = WeaponCreator.createWeapon(level, weaponName(held), Vec3.ZERO, 0.0F, 0.0F, null, false);
        if (weapon == null) {
            return;
        }
        WeaponParam prm = new WeaponParam();
        prm.entity = player;
        prm.user = player;
        prm.setPosAndRot(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), player.getXRot());
        prm.option1 = targetId;
        prm.option2 = weapon.numMode > 0 ? Math.floorMod(mode, weapon.numMode) : 0;
        if (weapon.shot(prm) && !player.getAbilities().instabuild) {
            // Upstream: a one-shot launcher is used up, the others are left empty to reload.
            if (held.getMaxDamage() == 1) {
                held.shrink(1);
            } else if (held.getMaxDamage() > 1) {
                held.setDamageValue(held.getMaxDamage());
            }
        }
    }

    /** Upstream {@code W_EntityPlayer.consumeInventoryItem}; creative players keep their missiles. */
    private static boolean consumeBullet(Player player, Item bullet) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(bullet)) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                return true;
            }
        }
        return false;
    }

    /** Upstream {@code W_EntityPlayer.hasItem}. */
    public static boolean hasBullet(Player player, Item bullet) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(bullet)) {
                return true;
            }
        }
        return false;
    }
}
