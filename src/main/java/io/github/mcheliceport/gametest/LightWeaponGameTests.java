package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.weapon.AAMissileEntity;
import io.github.mcheliceport.entity.weapon.ATMissileEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.item.LightWeaponItem;
import io.github.mcheliceport.net.LightWeaponControlPayload;
import io.github.mcheliceport.registry.ModItems;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The hand-held Stinger and Javelin (docs/M6_PLAN.md), against upstream's
 * {@code MCH_ItemLightWeaponBase} and {@code PacketPlayerLightWeaponControl}.
 */
final class LightWeaponGameTests {

    private LightWeaponGameTests() {
    }

    static void register() {
        ModGameTests.test("lweapon_items_aim_like_bows", 20, LightWeaponGameTests::items);
        ModGameTests.test("lweapon_stinger_fires_at_locked_target", 20, LightWeaponGameTests::stingerFires);
        ModGameTests.test("lweapon_refuses_invalid_shots", 20, LightWeaponGameTests::refusals);
        ModGameTests.test("lweapon_javelin_top_attack", 20, LightWeaponGameTests::javelinTopAttack);
        ModGameTests.test("lweapon_reload_uses_missile", 20, LightWeaponGameTests::reload);
        ModGameTests.test("lweapon_night_vision_while_aiming", 20, LightWeaponGameTests::nightVision);
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper h, GameType mode, Item weapon) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(mode);
        Vec3 pos = h.absoluteVec(new Vec3(2.5, 1, 2.5));
        p.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(weapon));
        return p;
    }

    /** Raises the sight: starts using the launcher and runs the player for {@code ticks} ticks. */
    private static void aim(ServerPlayer player, int ticks) {
        player.startUsingItem(InteractionHand.MAIN_HAND);
        for (int i = 0; i < ticks; i++) {
            player.doTick();
        }
    }

    private static Pig pig(GameTestHelper h, Vec3 relative) {
        Pig pig = EntityTypes.PIG.create(h.getLevel(), EntitySpawnReason.COMMAND);
        Vec3 pos = h.absoluteVec(relative);
        pig.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        pig.setNoAi(true);
        h.getLevel().addFreshEntity(pig);
        return pig;
    }

    private static <T extends BaseBulletEntity> List<T> missiles(GameTestHelper h, Class<T> type, ServerPlayer p) {
        return h.getLevel().getEntitiesOfClass(type, p.getBoundingBox().inflate(16.0),
                m -> m.shootingEntity == p);
    }

    private static LightWeaponControlPayload fire(Entity target, int mode) {
        return new LightWeaponControlPayload(true, target.getId(), mode, false, (byte) 0);
    }

    private static void items(GameTestHelper h) {
        check(h, ModItems.FIM92.get() instanceof LightWeaponItem stinger && stinger.bullet() == ModItems.FIM92_BULLET.get()
                && ModItems.FGM148.get() instanceof LightWeaponItem javelin
                && javelin.bullet() == ModItems.FGM148_BULLET.get(), "each launcher reloads with its own missile");
        ServerPlayer player = player(h, GameType.SURVIVAL, ModItems.FIM92.get());
        ItemStack stack = player.getMainHandItem();
        check(h, stack.getMaxDamage() == 10 && stack.getMaxStackSize() == 1
                && new ItemStack(ModItems.FIM92_BULLET.get()).getMaxStackSize() == 2, "upstream's stack sizes");
        check(h, stack.getItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME
                && player.isUsingItem(), "use raises the sight");
        check(h, stack.getUseAnimation() == ItemUseAnimation.BOW && stack.getUseDuration(player) == 72000,
                "it is held up like a bow, for as long as use is held");
        check(h, stack.getItem().onEntitySwing(stack, player, InteractionHand.MAIN_HAND), "the launcher is not swung");
        check(h, LightWeaponItem.weaponName(stack).equals("fim92")
                && LightWeaponItem.weaponName(new ItemStack(ModItems.FGM148.get())).equals("fgm148"),
                "the launchers fire their weapon definitions");
        check(h, !LightWeaponItem.isHeld(player), "not aimed before ten ticks");
        aim(player, 11);
        check(h, LightWeaponItem.isHeld(player), "aimed after ten ticks");
        h.succeed();
    }

    private static void stingerFires(GameTestHelper h) {
        ServerPlayer player = player(h, GameType.SURVIVAL, ModItems.FIM92.get());
        Pig target = pig(h, new Vec3(2.5, 1, 12.5));
        aim(player, 15);
        LightWeaponItem.handleControl(player, fire(target, 0));
        List<AAMissileEntity> fired = missiles(h, AAMissileEntity.class, player);
        check(h, fired.size() == 1, "one Stinger missile");
        AAMissileEntity missile = fired.getFirst();
        check(h, missile.targetEntity == target, "the missile chases the locked target");
        check(h, missile.position().distanceTo(player.getEyePosition()) < 0.5, "it leaves the player's eyes");
        check(h, !LightWeaponItem.isLoaded(player.getMainHandItem()), "the launcher is left empty");
        LightWeaponItem.handleControl(player, fire(target, 0));
        check(h, missiles(h, AAMissileEntity.class, player).size() == 1, "an empty launcher does not fire");
        fired.forEach(Entity::discard);
        target.discard();
        h.succeed();
    }

    private static void refusals(GameTestHelper h) {
        ServerPlayer player = player(h, GameType.SURVIVAL, ModItems.FIM92.get());
        Pig near = pig(h, new Vec3(2.5, 1, 12.5));
        LightWeaponItem.handleControl(player, fire(near, 0));
        check(h, missiles(h, AAMissileEntity.class, player).isEmpty() && LightWeaponItem.isLoaded(player.getMainHandItem()),
                "no shot without raising the sight");
        aim(player, 15);
        LightWeaponItem.handleControl(player, fire(player, 0));
        check(h, missiles(h, AAMissileEntity.class, player).isEmpty(), "the player cannot target themself");
        LightWeaponItem.handleControl(player, new LightWeaponControlPayload(true, -12345, 0, false, (byte) 0));
        check(h, missiles(h, AAMissileEntity.class, player).isEmpty(), "no shot at a target that does not exist");
        double range = ServerConfig.STINGER_LOCK_RANGE.get();
        try {
            // Ten blocks is beyond a lock range of 4 (6 with the server's slack).
            ServerConfig.STINGER_LOCK_RANGE.set(4.0);
            LightWeaponItem.handleControl(player, fire(near, 0));
        } finally {
            ServerConfig.STINGER_LOCK_RANGE.set(range);
        }
        check(h, missiles(h, AAMissileEntity.class, player).isEmpty() && LightWeaponItem.isLoaded(player.getMainHandItem()),
                "no shot at a target beyond the lock range");
        near.discard();
        h.succeed();
    }

    private static void javelinTopAttack(GameTestHelper h) {
        ServerPlayer player = player(h, GameType.CREATIVE, ModItems.FGM148.get());
        Pig target = pig(h, new Vec3(2.5, 1, 12.5));
        aim(player, 15);
        LightWeaponItem.handleControl(player, fire(target, 1));
        List<ATMissileEntity> fired = missiles(h, ATMissileEntity.class, player);
        check(h, fired.size() == 1 && fired.getFirst().guidanceType == 1, "mode 1 fires a top-attack Javelin");
        check(h, LightWeaponItem.isLoaded(player.getMainHandItem()), "a creative player's launcher stays loaded");
        LightWeaponItem.handleControl(player, fire(target, 7));
        List<ATMissileEntity> again = missiles(h, ATMissileEntity.class, player);
        check(h, again.size() == 2 && again.stream().filter(m -> m.guidanceType == 1).count() == 2,
                "an out-of-range mode wraps round the Javelin's two modes");
        again.forEach(Entity::discard);
        target.discard();
        h.succeed();
    }

    private static void reload(GameTestHelper h) {
        LightWeaponControlPayload reload = new LightWeaponControlPayload(false, 0, 0, true, (byte) 0);
        ServerPlayer player = player(h, GameType.SURVIVAL, ModItems.FIM92.get());
        ItemStack launcher = player.getMainHandItem();
        launcher.setDamageValue(launcher.getMaxDamage());
        LightWeaponItem.handleControl(player, reload);
        check(h, !LightWeaponItem.isLoaded(launcher), "no reload without a missile");
        player.getInventory().add(new ItemStack(ModItems.FGM148_BULLET.get()));
        LightWeaponItem.handleControl(player, reload);
        check(h, !LightWeaponItem.isLoaded(launcher), "the Javelin's missile does not fit the Stinger");
        player.getInventory().add(new ItemStack(ModItems.FIM92_BULLET.get(), 2));
        LightWeaponItem.handleControl(player, reload);
        check(h, launcher.getDamageValue() == 0 && player.getInventory().countItem(ModItems.FIM92_BULLET.get()) == 1,
                "a reload loads the launcher and uses one missile");
        ServerPlayer creative = player(h, GameType.CREATIVE, ModItems.FIM92.get());
        creative.getMainHandItem().setDamageValue(10);
        creative.getInventory().add(new ItemStack(ModItems.FIM92_BULLET.get()));
        LightWeaponItem.handleControl(creative, reload);
        check(h, creative.getMainHandItem().getDamageValue() == 0
                && creative.getInventory().countItem(ModItems.FIM92_BULLET.get()) == 1,
                "a creative player reloads without using the missile");
        h.succeed();
    }

    private static void nightVision(GameTestHelper h) {
        LightWeaponControlPayload on = new LightWeaponControlPayload(false, 0, 0, false,
                LightWeaponControlPayload.NIGHT_VISION_ON);
        LightWeaponControlPayload off = new LightWeaponControlPayload(false, 0, 0, false,
                LightWeaponControlPayload.NIGHT_VISION_OFF);
        ServerPlayer player = player(h, GameType.SURVIVAL, ModItems.FGM148.get());
        LightWeaponItem.handleControl(player, on);
        check(h, !player.hasEffect(MobEffects.NIGHT_VISION), "no night vision without aiming");
        aim(player, 15);
        LightWeaponItem.handleControl(player, on);
        MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
        check(h, nv != null && nv.getDuration() == 255, "aiming switches night vision on");
        player.removeEffect(MobEffects.NIGHT_VISION);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 100, 0, false, false));
        ItemStack stack = player.getMainHandItem();
        stack.getItem().onUseTick(h.getLevel(), player, stack, 100);
        nv = player.getEffect(MobEffects.NIGHT_VISION);
        check(h, nv != null && nv.getDuration() == 250, "aiming keeps night vision from running out");
        LightWeaponItem.handleControl(player, off);
        check(h, !player.hasEffect(MobEffects.NIGHT_VISION), "the camera key switches it off");
        h.succeed();
    }
}
