package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;
import static io.github.mcheliceport.gametest.GameTestSupport.player;
import static io.github.mcheliceport.gametest.GameTestSupport.spawn;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.weapon.WeaponSet;
import java.util.ArrayList;
import java.util.EnumSet;
import net.minecraft.nbt.NbtOps;
import io.github.mcheliceport.entity.aircraft.AircraftItemData;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.registry.ModDataComponents;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Exit criterion E8: punching a helicopter back into its item, which keeps its fuel and damage. */
final class PickUpGameTests {

    private PickUpGameTests() {
    }

    static void register() {
        ModGameTests.test("pickup_survival", 20, PickUpGameTests::survival);
        ModGameTests.test("pickup_occupied", 20, PickUpGameTests::occupied);
        ModGameTests.test("pickup_creative", 20, PickUpGameTests::creative);
        ModGameTests.test("pickup_item_data", 20, PickUpGameTests::itemData);
        ModGameTests.test("pickup_ammo_all_vehicles", 100, PickUpGameTests::allVehicleAmmo);
        ModGameTests.test("pickup_ammo_legacy_and_invalid", 20, PickUpGameTests::legacyAndInvalidAmmo);
    }

    private static void punch(GameTestHelper helper, HeliEntity heli, Player player) {
        heli.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1.0F);
    }

    private static List<ItemEntity> drops(GameTestHelper helper) {
        return helper.getEntities(EntityTypes.ITEM);
    }

    private static void defaults(Runnable body) {
        GameTestSupport.withConfig(CommonConfig.PREVENTING_BROKEN, false, () ->
                GameTestSupport.withConfig(CommonConfig.BREAKABLE_ONLY_PICKAXE, false, () ->
                        GameTestSupport.withConfig(CommonConfig.ITEM_FUEL, true, () ->
                                GameTestSupport.withConfig(CommonConfig.ITEM_DAMAGE, true, body))));
    }

    private static void survival(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        List<Integer> ammo = seedAmmo(heli, 1);
        heli.setFuel(345);
        heli.setDamageTaken(20);
        Player player = player(helper, GameType.SURVIVAL, new Vec3(12.5, 1.0, 9.5));
        defaults(() -> punch(helper, heli, player));
        check(helper, heli.isRemoved(), "a survival punch picks up an empty helicopter");
        List<ItemEntity> drops = drops(helper);
        check(helper, drops.size() == 1, "it drops its item: " + drops.size());
        ItemStack stack = drops.getFirst().getItem();
        check(helper, stack.getItem() == GameTestSupport.item(AH64), "the AH-64's item");
        check(helper, Integer.valueOf(345).equals(stack.get(ModDataComponents.AIRCRAFT_FUEL.get())),
                "with its fuel: " + stack.get(ModDataComponents.AIRCRAFT_FUEL.get()));
        check(helper, stack.getDamageValue() == 20, "and its damage: " + stack.getDamageValue());
        HeliEntity placed = spawn(helper, AH64, new Vec3(4.5, 1.0, 4.5), 0.0F);
        placed.readDataFromItem(stack);
        checkAmmo(helper, placed, ammo, "survival pickup");
        GameTestSupport.cleanUp(placed);
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    private static void occupied(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        Player pilot = player(helper, GameType.SURVIVAL, new Vec3(12.5, 1.0, 9.5));
        heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
        check(helper, heli.getPilot() == pilot, "the pilot boarded");
        Player player = player(helper, GameType.SURVIVAL, new Vec3(12.5, 1.0, 9.5));
        defaults(() -> punch(helper, heli, player));
        check(helper, !heli.isRemoved(), "an occupied helicopter is not picked up");
        check(helper, drops(helper).isEmpty(), "nothing dropped");
        check(helper, heli.getDamageTaken() == 1, "the punch did its damage instead: " + heli.getDamageTaken());
        GameTestSupport.cleanUp(heli);
        helper.succeed();
    }

    private static void creative(GameTestHelper helper) {
        for (boolean dropInCreative : new boolean[] {true, false}) {
            for (boolean sneaking : new boolean[] {false, true}) {
                HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
                Player player = player(helper, GameType.CREATIVE, new Vec3(12.5, 1.0, 9.5));
                player.setShiftKeyDown(sneaking);
                GameTestSupport.withConfig(CommonConfig.DROP_ITEM_IN_CREATIVE_MODE, dropInCreative,
                        () -> defaults(() -> punch(helper, heli, player)));
                check(helper, heli.isRemoved(), "a creative punch removes the helicopter");
                boolean expectDrop = dropInCreative != sneaking;
                List<ItemEntity> drops = drops(helper);
                check(helper, drops.size() == (expectDrop ? 1 : 0), "DropItemInCreativeMode " + dropInCreative
                        + ", sneaking " + sneaking + ": drops " + drops.size());
                drops.forEach(ItemEntity::discard);
            }
        }
        helper.succeed();
    }

    private static void itemData(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        heli.setFuel(500);
        heli.setDamageTaken(33);
        heli.setCommandForce("say landed");
        ItemStack stack = GameTestSupport.stack(AH64);
        defaults(() -> heli.writeDataToItem(stack));
        HeliEntity placed = spawn(helper, AH64, new Vec3(4.5, 1.0, 4.5), 0.0F);
        defaults(() -> placed.readDataFromItem(stack));
        check(helper, placed.getFuel() == 500 && placed.getDamageTaken() == 33
                && placed.getCommand().equals("say landed"), "fuel, damage and command survive the item");

        ItemStack plain = GameTestSupport.stack(AH64);
        GameTestSupport.withConfig(CommonConfig.ITEM_FUEL, false, () -> GameTestSupport.withConfig(
                CommonConfig.ITEM_DAMAGE, false, () -> heli.writeDataToItem(plain)));
        check(helper, !plain.has(ModDataComponents.AIRCRAFT_FUEL.get()) && plain.getDamageValue() == 0,
                "ItemFuel and ItemDamage off: the item keeps neither");
        HeliEntity fresh = spawn(helper, AH64, new Vec3(20.5, 1.0, 20.5), 0.0F);
        GameTestSupport.withConfig(CommonConfig.ITEM_FUEL, false, () -> GameTestSupport.withConfig(
                CommonConfig.ITEM_DAMAGE, false, () -> fresh.readDataFromItem(stack)));
        check(helper, fresh.getFuel() == 0 && fresh.getDamageTaken() == 0, "and placing ignores them");
        check(helper, AircraftItemData.read(GameTestSupport.stack(AH64), true, true) == null,
                "a new item carries no vehicle data");
        GameTestSupport.cleanUp(heli);
        GameTestSupport.cleanUp(placed);
        GameTestSupport.cleanUp(fresh);
        helper.succeed();
    }

    /** Full, partial and empty magazines/reserves survive repeated serialized item round trips. */
    private static void allVehicleAmmo(GameTestHelper helper) {
        EnumSet<ContentType> covered = EnumSet.noneOf(ContentType.class);
        for (ModItems.ContentItem entry : ModItems.contentItems()) {
            if (!(entry.item().get() instanceof AircraftItem item) || !item.isPlaceable()) {
                continue;
            }
            covered.add(entry.type());
            for (int state = 0; state < 3; state++) {
                AircraftEntity aircraft = item.createAircraft(helper.getLevel(), 0, 20, 0);
                check(helper, aircraft != null, entry.contentName() + " creates a vehicle");
                List<Integer> expected = seedAmmo(aircraft, state);
                for (int cycle = 0; cycle < 2; cycle++) {
                    ItemStack stack = new ItemStack(item);
                    AircraftEntity source = aircraft;
                    GameTestSupport.withConfig(CommonConfig.ITEM_FUEL, false, () ->
                            GameTestSupport.withConfig(CommonConfig.ITEM_DAMAGE, false,
                                    () -> source.writeDataToItem(stack)));
                    var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
                    var tag = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                    ItemStack restored = ItemStack.CODEC.parse(ops, tag).getOrThrow();
                    GameTestSupport.cleanUp(aircraft);
                    aircraft = item.createAircraft(helper.getLevel(), 0, 20, 0);
                    check(helper, aircraft != null, "recreates " + entry.contentName());
                    aircraft.readDataFromItem(restored);
                    checkAmmo(helper, aircraft, expected, entry.contentName() + " cycle " + cycle);
                }
                GameTestSupport.cleanUp(aircraft);
            }
        }
        check(helper, covered.containsAll(EnumSet.of(ContentType.HELICOPTER, ContentType.PLANE,
                ContentType.TANK, ContentType.SHIP, ContentType.VEHICLE)), "all five vehicle families tested");
        helper.succeed();
    }

    private static List<Integer> seedAmmo(AircraftEntity aircraft, int state) {
        List<Integer> counts = new ArrayList<>();
        for (WeaponSet weapon : aircraft.weapons().all()) {
            int capacity = Math.max(0, weapon.getMaxAmmo());
            int loaded = state == 2 ? 0 : Math.min(Math.max(0, weapon.getMagSize()), capacity);
            int reserve = state == 2 ? 0 : capacity - loaded;
            if (state == 1) {
                loaded /= 2;
                reserve /= 2;
            }
            weapon.setAmmo(loaded);
            weapon.setReserveAmmo(reserve);
            counts.add(loaded);
            counts.add(reserve);
        }
        return List.copyOf(counts);
    }

    private static void checkAmmo(GameTestHelper helper, AircraftEntity aircraft, List<Integer> expected,
            String label) {
        check(helper, aircraft.weapons().getWeaponNum() * 2 == expected.size(), label + ": weapon count");
        for (int i = 0; i < aircraft.weapons().getWeaponNum(); i++) {
            WeaponSet weapon = aircraft.weapons().getWeapon(i);
            check(helper, weapon.getAmmo() == expected.get(i * 2)
                    && weapon.getRestAllAmmoNum() == expected.get(i * 2 + 1),
                    label + ": exact magazine/reserve for weapon " + i);
        }
    }

    private static void legacyAndInvalidAmmo(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        List<Integer> expected = seedAmmo(heli, 1);
        heli.readDataFromItem(GameTestSupport.stack(AH64));
        checkAmmo(helper, heli, expected, "new item keeps defaults");
        ItemStack legacy = GameTestSupport.stack(AH64);
        legacy.set(ModDataComponents.AIRCRAFT_COMMAND.get(), "");
        heli.readDataFromItem(legacy);
        checkAmmo(helper, heli, expected, "legacy item keeps defaults");

        ItemStack invalid = GameTestSupport.stack(AH64);
        // A component alone is sufficient, and a trailing unmatched entry is ignored.
        invalid.set(ModDataComponents.AIRCRAFT_AMMO.get(), List.of(Integer.MAX_VALUE, Integer.MAX_VALUE, 5));
        heli.readDataFromItem(invalid);
        WeaponSet first = heli.weapons().getWeapon(0);
        int capacity = Math.max(0, first.getMaxAmmo());
        int magazine = Math.max(0, Math.min(first.getMagSize(), capacity));
        check(helper, first.getAmmo() == magazine && first.getRestAllAmmoNum() == capacity - magazine,
                "oversized counts are capped without overflowing");
        for (int i = 1; i < heli.weapons().getWeaponNum(); i++) {
            check(helper, heli.weapons().getWeapon(i).getAmmo() == expected.get(i * 2)
                    && heli.weapons().getWeapon(i).getRestAllAmmoNum() == expected.get(i * 2 + 1),
                    "missing pairs leave weapon " + i + " unchanged");
        }
        invalid.set(ModDataComponents.AIRCRAFT_AMMO.get(), List.of(-1, -1));
        heli.readDataFromItem(invalid);
        check(helper, first.getAmmo() == 0 && first.getRestAllAmmoNum() == 0, "negative counts become zero");
        GameTestSupport.cleanUp(heli);
        helper.succeed();
    }
}
