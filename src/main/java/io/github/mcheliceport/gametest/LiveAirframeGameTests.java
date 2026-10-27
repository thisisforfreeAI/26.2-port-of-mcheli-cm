package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.damage.*;
import io.github.mcheliceport.effect.VehicleImpactLook;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Hits on stock airborne planes disable structure while leaving hull HP, save state and repairs valid. */
final class LiveAirframeGameTests {
    private LiveAirframeGameTests() {}
    static void register(){
        ModGameTests.test("live_airframe_wing_hits",30,LiveAirframeGameTests::wings);
        ModGameTests.test("live_airframe_tail_blast",30,LiveAirframeGameTests::tail);
        ModGameTests.test("live_airframe_save_and_repair",30,LiveAirframeGameTests::saveRepair);
    }
    private static AircraftEntity spawn(GameTestHelper h,String name){
        var item=(AircraftItem)ModItems.contentItems().stream().filter(c->c.type()==ContentType.PLANE&&c.contentName().equals(name)).findFirst().orElseThrow().item().get();
        Vec3 p=h.absoluteVec(new Vec3(12,20,12));var ac=item.createAircraft(h.getLevel(),p.x,p.y,p.z);h.getLevel().addFreshEntity(ac);ac.tick();return ac;
    }
    private static Vec3 world(AircraftEntity ac,Vec3 local){
        var p=VehicleImpactLook.body(ac.getYaw(),ac.getPitch(),ac.getRoll()).transformPosition(new Vector3f((float)local.x,(float)local.y,(float)local.z));
        return ac.position().add(p.x,p.y,p.z);
    }
    private static void wing(GameTestHelper h,AircraftEntity ac,int side){
        var zone=VehicleComponents.of(ac).stream().filter(v->v.kind()==ComponentKind.WING&&v.index()==side).findFirst().orElseThrow();
        int sign=side==0?-1:1,bit=side==0?1:2;
        var bullet=new BulletEntity(ModEntityTypes.BULLET.get(),h.getLevel());
        Vec3 from=world(ac,zone.center().add(sign*(zone.halfSize().x+2),0,0)),toward=world(ac,zone.center()).subtract(from);
        bullet.setPos(from);bullet.setDeltaMovement(toward);
        var source=h.getLevel().damageSources().thrown(bullet,null);
        for(int i=0;i<30&&(AirframeDamage.missing(ac)&bit)==0&&ac.getDamageTaken()<ac.getMaxHP()*.7;i++){
            ac.timeSinceHit=0;ac.hurtServer(h.getLevel(),source,ac.getMaxHP()*.15F);
        }
        check(h,!ac.isDestroyed()&&AirframeDamage.missing(ac)==bit,"only the hit wing fails while hull survives: "+VehicleComponents.of(ac));
        check(h,ac.getY()>h.absoluteVec(Vec3.ZERO).y+10,"fixture is airborne");
        check(h,ComponentEffects.throttleCap(ac)==1,"structural visuals do not disable intact engines");
    }
    private static void wings(GameTestHelper h){
        for(String name:new String[]{"f-35a","f-15e","a-10"})for(int side=0;side<2;side++){
            var ac=spawn(h,name);wing(h,ac,side);GameTestSupport.cleanUp(ac);
        }
        h.succeed();
    }
    private static void tail(GameTestHelper h){
        var ac=spawn(h,"a-10");var tail=VehicleComponents.first(ac,ComponentKind.AIRFRAME_TAIL);
        var type=h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.EXPLOSION);
        var source=new DamageSource(type,world(ac,tail.center()));
        for(int i=0;i<10&&(AirframeDamage.missing(ac)&4)==0&&ac.getDamageTaken()<ac.getMaxHP()*.7;i++){
            ac.timeSinceHit=0;ac.hurtServer(h.getLevel(),source,ac.getMaxHP()*.15F);
        }
        check(h,!ac.isDestroyed()&&(AirframeDamage.missing(ac)&4)!=0,"tail blast failure precedes hull destruction");
        GameTestSupport.cleanUp(ac);h.succeed();
    }
    private static void saveRepair(GameTestHelper h){
        var ac=spawn(h,"f-35a");wing(h,ac,0);
        var saved=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,h.getLevel().registryAccess());ac.save(saved);
        var loaded=spawn(h,"f-35a");loaded.load(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),saved.buildResult()));
        check(h,AirframeDamage.missing(loaded)==1&&!loaded.isDestroyed(),"airborne wing loss persists across reload");
        loaded.repair(loaded.getMaxHP());
        check(h,AirframeDamage.missing(loaded)==0&&loaded.getComponentHealth()==ComponentHealth.FULL,"full repairs restore structural health");
        GameTestSupport.cleanUp(ac);GameTestSupport.cleanUp(loaded);h.succeed();
    }
}
