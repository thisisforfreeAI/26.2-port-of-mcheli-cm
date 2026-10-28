package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;
import static io.github.mcheliceport.gametest.GameTestSupport.withConfig;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.content.aircraft.TankInfo;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.effect.VehicleBlood;
import io.github.mcheliceport.effect.VehicleBloodRules;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.weapon.explosion.McheliExplosion;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Real hurt/kill paths; these also run unchanged in the NTM Next CI configuration. */
final class VehicleBloodGameTests {
    private VehicleBloodGameTests() {}
    static void register() {
        ModGameTests.test("vehicle_blood_direct_and_dedup",30,VehicleBloodGameTests::direct);
        ModGameTests.test("vehicle_blood_conventional_blast",30,VehicleBloodGameTests::blast);
        ModGameTests.test("vehicle_blood_ntm_portable_and_nonflesh_exclusions",30,VehicleBloodGameTests::exclusions);
        ModGameTests.test("vehicle_blood_totem_and_cancelled_death",30,VehicleBloodGameTests::survival);
        ModGameTests.test("vehicle_blood_tank_ram",30,VehicleBloodGameTests::ram);
        ModGameTests.test("vehicle_blood_unrelated_death_while_riding",30,VehicleBloodGameTests::unrelated);
    }
    private static AircraftEntity heli(GameTestHelper h) {
        return GameTestSupport.spawn(h,"ah-64",new Vec3(12,4,12),0);
    }
    private static Cow cow(GameTestHelper h) {
        Cow cow=EntityTypes.COW.create(h.getLevel(),EntitySpawnReason.COMMAND);
        cow.setPos(h.absoluteVec(new Vec3(8,4,8))); cow.setNoAi(true); cow.setNoGravity(true);
        h.getLevel().addFreshEntity(cow); return cow;
    }
    private static WeaponInfo info() { return new WeaponInfo(ContentLocation.builtin("blood_fixture"),"test","txt"); }
    private static BulletEntity bullet(GameTestHelper h, WeaponInfo info, AircraftEntity vehicle) {
        var bullet=new BulletEntity(ModEntityTypes.BULLET.get(),h.getLevel()) {
            @Override public WeaponInfo getInfo() { return info; }
            @Override public WeaponInfo getWeaponInfo() { return info; }
        };
        bullet.shootingAircraft=vehicle; bullet.setPower(1000); bullet.setDeltaMovement(0,0,1);
        bullet.setPos(h.absoluteVec(new Vec3(8,4,6))); return bullet;
    }
    private static boolean reported(GameTestHelper h, Mob mob) { return VehicleBlood.reported(h.getLevel(),mob.getUUID()); }
    private static void direct(GameTestHelper h) {
        var vehicle=heli(h);var cow=cow(h);var bullet=bullet(h,info(),vehicle);
        bullet.setPower(1);bullet.onImpactEntity(cow,1);
        check(h,cow.isAlive()&&!reported(h,cow),"nonlethal vehicle hits emit no blood");
        cow.invulnerableTime=0;bullet.setPower(1000);
        bullet.onImpactEntity(cow,1);
        check(h,!cow.isAlive()&&reported(h,cow),"a real conventional vehicle round reports its lethal hit");
        bullet.onImpactEntity(cow,1);
        cow.discard();var second=cow(h);
        var ds=h.getLevel().damageSources().thrown(bullet,null);
        var duplicate=VehicleBlood.projectile(second,bullet,ds,VehicleBloodRules.DIRECT,Vec3.ZERO);
        boolean applied=second.hurtServer(h.getLevel(),ds,1000);
        check(h,VehicleBlood.finish(duplicate,applied)!=null,"the explicit damage scope confirms its kill");
        check(h,VehicleBlood.finish(duplicate,applied)==null,"the same lethal scope can emit only once");
        second.discard();GameTestSupport.cleanUp(vehicle);h.succeed();
    }
    private static void blast(GameTestHelper h) {
        var vehicle=heli(h);var cow=cow(h);var bullet=bullet(h,info(),vehicle);
        var explosion=new McheliExplosion(h.getLevel(),bullet,null,cow.getX()-1,cow.getY()+.5,cow.getZ(),3,0,false,false);
        explosion.explosionDamagePower=100;explosion.doExplosionA();
        check(h,!cow.isAlive()&&reported(h,cow),"conventional projectile blast reports blood without block/crater changes");
        cow.discard();GameTestSupport.cleanUp(vehicle);h.succeed();
    }
    private static void exclusions(GameTestHelper h) {
        var vehicle=heli(h);
        for(int mode=0;mode<3;mode++) {
            var cow=cow(h);var info=info();if(mode==0)info.useHBM=true;
            if(mode==1)info.payloadNTM=WeaponInfo.Payload.values()[1];
            var bullet=bullet(h,info,mode==2?null:vehicle);bullet.onImpactEntity(cow,1);
            check(h,!cow.isAlive()&&!reported(h,cow),"NTM flag, NTM payload and portable weapons are excluded: "+mode);
            cow.discard();
        }
        var skeleton=EntityTypes.SKELETON.create(h.getLevel(),EntitySpawnReason.COMMAND);
        skeleton.setPos(h.absoluteVec(new Vec3(8,4,8)));h.getLevel().addFreshEntity(skeleton);
        bullet(h,info(),vehicle).onImpactEntity(skeleton,1);
        check(h,!skeleton.isAlive()&&!reported(h,skeleton),"skeletons have no blood");
        skeleton.discard();GameTestSupport.cleanUp(vehicle);h.succeed();
    }
    private static void survival(GameTestHelper h) {
        var vehicle=heli(h);var cow=cow(h);var bullet=bullet(h,info(),vehicle);
        Consumer<LivingDeathEvent> cancel=e->{if(e.getEntity()==cow)e.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class,cancel);
        try { bullet.onImpactEntity(cow,1); }
        finally { NeoForge.EVENT_BUS.unregister(cancel); }
        check(h,cow.getHealth()==0&&!reported(h,cow),"cancelled death with zero health still produces no blood");
        cow.discard();var saved=cow(h);
        saved.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.TOTEM_OF_UNDYING));
        bullet.onImpactEntity(saved,1);
        check(h,saved.isAlive()&&!reported(h,saved),"totem death protection is respected");
        saved.discard();GameTestSupport.cleanUp(vehicle);h.succeed();
    }
    private static void ram(GameTestHelper h) {
        var definition=new TankInfo(ContentLocation.builtin("blood_ram"),"test","yaml");
        definition.weightType=2;definition.maxHp=1000;definition.speed=1;definition.seatList.add(new SeatInfo(Vec3.ZERO,false));
        var tank=new TankEntity(ModEntityTypes.TANK.get(),h.getLevel()) {
            @Override protected AircraftInfo lookupInfo(String name) { return definition; }
            @Override protected void onUpdateAircraft() {}
        };
        tank.setPos(h.absoluteVec(new Vec3(12.5,6,12.5)));tank.setAircraftTypeName("blood_ram");
        h.getLevel().addFreshEntity(tank);tank.tick();var cow=cow(h);cow.setPos(tank.position().add(.1,0,0));cow.setHealth(1);
        tank.setDeltaMovement(.2,0,0);
        withConfig(CommonConfig.COLLISION_ENTITY_DAMAGE,true,()->
                withConfig(CommonConfig.COLLISION_DESTROY_BLOCK,false,tank::tick));
        check(h,!cow.isAlive()&&reported(h,cow),"actual tank collision reports its own lethal ram: hp="+cow.getHealth()+" info="+tank.getTankInfo()+" motion="+tank.getDeltaMovement());
        cow.discard();GameTestSupport.cleanUp(tank);h.succeed();
    }
    private static void unrelated(GameTestHelper h) {
        var vehicle=heli(h);var player=h.makeMockServerPlayerInLevel();player.startRiding(vehicle,true,true);
        var cow=cow(h);var bullet=bullet(h,info(),vehicle);bullet.setPower(1);bullet.onImpactEntity(cow,1);
        cow.invulnerableTime=0;cow.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(player),1000);
        check(h,!cow.isAlive()&&!reported(h,cow),"a later gun/player death while riding never inherits old vehicle damage");
        cow.discard();player.stopRiding();GameTestSupport.cleanUp(vehicle);h.succeed();
    }
}
