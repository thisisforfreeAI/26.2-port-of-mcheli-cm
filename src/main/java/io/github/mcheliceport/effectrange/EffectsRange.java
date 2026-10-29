package io.github.mcheliceport.effectrange;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.net.*;
import io.github.mcheliceport.weapon.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** An operator/singleplayer-owner sandbox, with no edits to the player's ordinary dimensions. */
@EventBusSubscriber(modid=McheliCePort.MOD_ID)
public final class EffectsRange {
    public static final Identifier ID=Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID,"effects_range");
    public static final ResourceKey<Level> LEVEL=ResourceKey.create(Registries.DIMENSION,ID);
    public static final int ENTER=0,LEAVE=1,RESET=2,CONFIGURE=3,FIRE=4,MAX_SESSIONS=4,BLOCKS_PER_TICK=2048;
    private static final String RETURN="M8EffectsRangeReturn";
    private static final Map<UUID,Session> SESSIONS=new LinkedHashMap<>();
    private static int blocksLastTick,roundRobin;
    private static final class Session {
        final int slot,origin;final ArmorStand launcher;RangeOptions options,pending;int cursor=RangeOptions.TOTAL;
        long nextAction,nextShot;int shots;boolean cooling;
        Session(int slot,ServerLevel level,RangeOptions options){this.slot=slot;origin=slot*256;this.options=options;launcher=new ArmorStand(level,origin,36,-32);launcher.setNoGravity(true);launcher.setYRot(0);launcher.setXRot(48);}
    }
    private EffectsRange(){}
    public static boolean inRange(Level level){return level.dimension().equals(LEVEL);}
    public static boolean allowed(ServerPlayer player){return AircraftEntity.canSetCommand(player)||player.level().getServer().isSingleplayerOwner(new NameAndId(player.getGameProfile()));}
    public static int sessions(){return SESSIONS.size();}
    public static int blocksLastTick(){return blocksLastTick;}
    public static boolean ready(ServerPlayer player){var s=SESSIONS.get(player.getUUID());return s!=null&&s.cursor>=RangeOptions.TOTAL;}
    public static int shots(ServerPlayer player){var s=SESSIONS.get(player.getUUID());return s==null?0:s.shots;}
    private static void tell(ServerPlayer p,String text){p.sendSystemMessage(Component.literal(text));}
    private static void notice(ServerPlayer p,Session s,int state){ModNetwork.sendToPlayer(p,new EffectsRangeNoticePayload(state,s.options.weapon(),s.options.surface(),s.options.lighting(),(int)Math.clamp(s.nextShot-p.level().getGameTime(),0,2400)));}
    public static boolean act(ServerPlayer player,int action,int weapon,int surface,int lighting){
        if(action==LEAVE)return leave(player);
        if(action<ENTER||action>FIRE||!RangeOptions.valid(weapon,surface,lighting)||!allowed(player)){
            tell(player,"The effects test range requires the singleplayer owner or operator permission.");return false;
        }
        var options=new RangeOptions(weapon,surface,lighting);
        if(action==ENTER)return enter(player,options);
        var s=SESSIONS.get(player.getUUID());
        if(s==null||!inRange(player.level()))return false;
        if(action==CONFIGURE){s.pending=options;return true;}
        long now=player.level().getGameTime();if(now<s.nextAction)return false;s.nextAction=now+5;
        if(action==RESET){s.pending=null;s.options=options;lighting(player.level(),options);reset(player,s);return true;}
        if(!ready(player)){tell(player,"The target pad is still preparing.");return false;}
        if(s.options.surface()!=surface){tell(player,"Prepare the selected surface before firing.");return false;}
        if(now<s.nextShot){notice(player,s,2);tell(player,"Let this weapon's demonstration cooldown finish before firing again.");return false;}
        s.pending=null;s.options=options;lighting(player.level(),options);return fire(player,s,now);
    }
    private static boolean enter(ServerPlayer player,RangeOptions options){
        if(inRange(player.level())){tell(player,"You are already in the range; use Return when finished.");return false;}
        if(player.isPassenger()||player.isSleeping()){tell(player,"Dismount and wake up before entering the range.");return false;}
        var server=player.level().getServer();ServerLevel level=server.getLevel(LEVEL);
        if(level==null){tell(player,"The range dimension is unavailable. Restart the world after installing this feature.");return false;}
        if(SESSIONS.size()>=MAX_SESSIONS){tell(player,"All four range pads are occupied.");return false;}
        int slot=0;while(slot<MAX_SESSIONS){int candidate=slot;if(SESSIONS.values().stream().noneMatch(s->s.slot==candidate))break;slot++;}
        var data=new CompoundTag();var abilities=player.getAbilities();
        data.putBoolean("Flying",abilities.flying);data.putBoolean("MayFly",abilities.mayfly);data.putBoolean("Invulnerable",abilities.invulnerable);data.putBoolean("InstantBuild",abilities.instabuild);data.putBoolean("MayBuild",abilities.mayBuild);
        data.putFloat("FlySpeed",abilities.getFlyingSpeed());data.putFloat("WalkSpeed",abilities.getWalkingSpeed());
        data.putString("Dimension",player.level().dimension().identifier().toString());
        data.putDouble("X",player.getX());data.putDouble("Y",player.getY());data.putDouble("Z",player.getZ());data.putFloat("Yaw",player.getYRot());data.putFloat("Pitch",player.getXRot());data.putInt("Mode",player.gameMode().getId());
        player.getPersistentData().put(RETURN,data);var s=new Session(slot,level,options);
        player.closeContainer();player.setGameMode(GameType.SPECTATOR);
        if(!player.teleportTo(level,s.origin+28,9,34,Set.of(),140,12,true)){restoreMode(player,data);player.getPersistentData().remove(RETURN);return false;}
        player.setDeltaMovement(Vec3.ZERO);SESSIONS.put(player.getUUID(),s);lighting(level,options);reset(player,s);
        tell(player,"Effects range: spectator viewing, real weapon impacts. ESC > MC Heli Effects > Test range. Return restores your location and mode.");return true;
    }
    public static boolean leave(ServerPlayer player){
        var saved=player.getPersistentData().getCompound(RETURN);if(saved.isEmpty())return false;
        var data=saved.get();var server=player.level().getServer();var id=Identifier.tryParse(data.getStringOr("Dimension","minecraft:overworld"));
        ServerLevel target=id==null?null:server.getLevel(ResourceKey.create(Registries.DIMENSION,id));
        boolean fallback=target==null||inRange(target);if(fallback)target=server.overworld();
        double x=data.getDoubleOr("X",.5),y=data.getDoubleOr("Y",80),z=data.getDoubleOr("Z",.5);
        if(fallback||!Double.isFinite(x+y+z)||Math.abs(x)>29999984||Math.abs(z)>29999984){var spawn=target.getRespawnData().pos();x=spawn.getX()+.5;y=spawn.getY()+1;z=spawn.getZ()+.5;}
        var s=SESSIONS.get(player.getUUID());if(s!=null)cleanProjectiles((ServerLevel)s.launcher.level(),s);
        player.closeContainer();
        if(!player.teleportTo(target,x,y,z,Set.of(),data.getFloatOr("Yaw",0),data.getFloatOr("Pitch",0),true))return false;
        player.setDeltaMovement(Vec3.ZERO);restoreMode(player,data);
        player.getPersistentData().remove(RETURN);SESSIONS.remove(player.getUUID());return true;
    }
    private static void restoreMode(ServerPlayer player,CompoundTag data){
        player.setGameMode(GameType.byId(data.getIntOr("Mode",0)));
        var abilities=player.getAbilities();abilities.flying=data.getBooleanOr("Flying",abilities.flying);abilities.mayfly=data.getBooleanOr("MayFly",abilities.mayfly);abilities.invulnerable=data.getBooleanOr("Invulnerable",abilities.invulnerable);abilities.instabuild=data.getBooleanOr("InstantBuild",abilities.instabuild);abilities.mayBuild=data.getBooleanOr("MayBuild",abilities.mayBuild);
        abilities.setFlyingSpeed(data.getFloatOr("FlySpeed",abilities.getFlyingSpeed()));abilities.setWalkingSpeed(data.getFloatOr("WalkSpeed",abilities.getWalkingSpeed()));player.onUpdateAbilities();
    }
    private static void lighting(Level world,RangeOptions options){
        if(!(world instanceof ServerLevel level)||!inRange(level))return;
        level.dimensionType().defaultClock().filter(clock->clock.is(ResourceKey.create(Registries.WORLD_CLOCK,ID))).ifPresent(clock->{
            var manager=level.clockManager();manager.setTotalTicks(clock,options.time());manager.setPaused(clock,true);
            for(var player:level.players())player.connection.send(manager.createFullSyncPacket());
        });
        var weather=level.getWeatherData();weather.setClearWeatherTime(24000);weather.setRainTime(0);weather.setThunderTime(0);weather.setRaining(false);weather.setThundering(false);
    }
    private static void reset(ServerPlayer player,Session s){
        cleanProjectiles((ServerLevel)s.launcher.level(),s);s.cursor=0;notice(player,s,1);tell(player,"Preparing the target pad...");
    }
    private static void cleanProjectiles(ServerLevel level,Session s){
        var owned=new ArrayList<net.minecraft.world.entity.Entity>();
        for(var e:level.getAllEntities())if(e instanceof BaseBulletEntity bullet&&(bullet.shootingEntity==s.launcher||bullet.shootingAircraft==s.launcher)
                ||e instanceof ItemEntity item&&item.getItem().getItem() instanceof BlockItem&&insidePad(s,e.getX(),e.getZ()))owned.add(e);
        owned.forEach(net.minecraft.world.entity.Entity::discard);
    }
    private static boolean insidePad(Session s,double x,double z){return Math.abs(x-s.origin)<=32&&Math.abs(z)<=32;}
    /** Temporary spectator targets do not retain terrain loot; ordinary dimensions and inventories are untouched. */
    @SubscribeEvent public static void terrainDrop(EntityJoinLevelEvent event){
        if(!inRange(event.getLevel())||event.getLevel().isClientSide()||!(event.getEntity() instanceof ItemEntity item)
                ||!(item.getItem().getItem() instanceof BlockItem))return;
        for(var s:SESSIONS.values())if(insidePad(s,item.getX(),item.getZ())){event.setCanceled(true);return;}
    }
    private static boolean fire(ServerPlayer player,Session s,long now){
        var level=(ServerLevel)player.level();String name=RangeOptions.WEAPONS.get(s.options.weapon());var info=WeaponCreator.info(name);
        if(!RangeOptions.conventional(info)){tell(player,"This demo weapon is unavailable or uses a separate payload integration.");return false;}
        boolean bomb="bomb".equals(info.type);double x=s.origin,y=36,z=bomb?0:-32;float pitch=bomb?90:48;
        s.launcher.setPos(x,y,z);s.launcher.setYRot(0);s.launcher.setXRot(pitch);s.launcher.setDeltaMovement(Vec3.ZERO);
        var weapon=WeaponCreator.createWeapon(level,name,Vec3.ZERO,0,0,null,false);if(weapon==null)return false;
        var prm=new WeaponParam();prm.entity=s.launcher;prm.user=s.launcher;prm.setPosAndRot(x,y,z,0,pitch);prm.option1=2;
        if(!weapon.use(prm))return false;
        s.nextShot=now+Math.max(100,Math.abs((long)info.delay));s.shots++;s.cooling=true;notice(player,s,2);
        tell(player,RangeOptions.WEAPON_LABELS.get(s.options.weapon())+" on "+RangeOptions.SURFACES.get(s.options.surface())+" — original weapon damage and crater settings.");return true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        blocksLastTick=0;if(SESSIONS.isEmpty())return;var server=event.getServer();var entries=new ArrayList<>(SESSIONS.entrySet());
        int visited=0;while(blocksLastTick<BLOCKS_PER_TICK&&visited++<entries.size()){
            var entry=entries.get(Math.floorMod(roundRobin++,entries.size()));var s=entry.getValue();var p=server.getPlayerList().getPlayer(entry.getKey());
            if(p==null||!inRange(p.level())){if(p!=null)leave(p);else{cleanProjectiles((ServerLevel)s.launcher.level(),s);SESSIONS.remove(entry.getKey());}continue;}
            var level=(ServerLevel)p.level();
            if(s.pending!=null){var requested=s.pending;s.pending=null;boolean ground=requested.surface()!=s.options.surface();s.options=requested;lighting(level,requested);if(ground)reset(p,s);else notice(p,s,3);}
            if(s.cooling&&level.getGameTime()>=s.nextShot){s.cooling=false;notice(p,s,4);}
            int slice=Math.min(Math.max(1,BLOCKS_PER_TICK/entries.size()),BLOCKS_PER_TICK-blocksLastTick);
            while(slice-->0&&s.cursor<RangeOptions.TOTAL){var at=RangeOptions.block(s.cursor++);var pos=new BlockPos(s.origin+at[0],at[1],at[2]);
                var block=at[1]>=0?Blocks.AIR:s.options.surface()==0?Blocks.SAND:s.options.surface()==1?(at[1]==-1?Blocks.GRASS_BLOCK:Blocks.DIRT):s.options.surface()==3?Blocks.WATER:Blocks.STONE;
                // The generated stone beneath -12 supports sand and contains the water basin.
                level.setBlock(pos,block.defaultBlockState(),2);blocksLastTick++;
            }
            if(s.cursor==RangeOptions.TOTAL){s.cursor++;notice(p,s,0);tell(p,"Target pad ready.");}
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)leave(p);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer p&&p.getPersistentData().contains(RETURN))leave(p);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){SESSIONS.clear();blocksLastTick=roundRobin=0;}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("mcheli_range")
            .then(Commands.literal("enter").executes(c->act(c.getSource().getPlayerOrException(),ENTER,0,0,0)?1:0))
            .then(Commands.literal("return").executes(c->leave(c.getSource().getPlayerOrException())?1:0)));
    }
}
