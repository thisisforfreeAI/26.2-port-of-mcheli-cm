package io.github.mcheliceport.config;

import io.github.mcheliceport.wingman.ai.AiSkill;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * SERVER config ({@code config/mcheli_ce_port-server.toml}; a copy in {@code <world>/serverconfig/}
 * overrides it for that world): settings the server owns, loaded when a server starts and synced to
 * every client that joins.
 *
 * <p>Upstream's world rules (rotation limits, lock ranges, marker settings, command permissions)
 * move here as they are ported (PORTING_PLAN.md section 5.6).
 */
public final class ServerConfig {
    /** Decision D2: a server can switch back to upstream's server-authoritative vehicle movement. */
    public static final ModConfigSpec.BooleanValue SERVER_AUTHORITATIVE_MOVEMENT;
    public static final ModConfigSpec.BooleanValue ENABLE_ROTATION_LIMIT;
    public static final ModConfigSpec.IntValue PITCH_LIMIT_MAX;
    public static final ModConfigSpec.IntValue PITCH_LIMIT_MIN;
    public static final ModConfigSpec.IntValue ROLL_LIMIT;
    public static final ModConfigSpec.BooleanValue ENABLE_CAM_DIST_CHANGE;
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_PAIRED_UAVS;
    public static final ModConfigSpec.BooleanValue TABLET_NORMAL_UAVS;
    public static final ModConfigSpec.IntValue UAV_CONTROL_RANGE;
    public static final ModConfigSpec.IntValue UAV_REMOTE_VIEW_DISTANCE;
    public static final ModConfigSpec.BooleanValue ENABLE_ENTITY_MARKER;
    public static final ModConfigSpec.IntValue RANGE_FINDER_SPOT_DIST;
    public static final ModConfigSpec.IntValue RANGE_FINDER_SPOT_TIME;
    public static final ModConfigSpec.BooleanValue RANGE_FINDER_CONSUME;
    public static final ModConfigSpec.DoubleValue STINGER_LOCK_RANGE;
    public static final ModConfigSpec.DoubleValue CHARGER_RANGE;
    public static final ModConfigSpec.IntValue CHARGER_TRANSFER_RATE;
    public static final ModConfigSpec.IntValue CHARGER_CAPACITY;
    public static final ModConfigSpec.BooleanValue GROUND_CREW;
    public static final ModConfigSpec.IntValue PAD_REPAIR_HP;
    public static final ModConfigSpec.DoubleValue PAD_SUPPLY_RANGE;
    public static final ModConfigSpec.DoubleValue WINGMAN_SIDE_DIST;
    public static final ModConfigSpec.DoubleValue WINGMAN_ALT_OFFSET;
    public static final ModConfigSpec.DoubleValue WINGMAN_REAR_DIST;
    public static final ModConfigSpec.IntValue WINGMAN_MAX_WINGMEN;
    public static final ModConfigSpec.DoubleValue WINGMAN_MIN_ATTACK_ALTITUDE;
    public static final ModConfigSpec.DoubleValue WINGMAN_MAX_ATTACK_ALTITUDE;
    public static final ModConfigSpec.BooleanValue NPC_BATTLES;
    public static final ModConfigSpec.EnumValue<AiSkill> AI_DIFFICULTY;
    public static final ModConfigSpec.BooleanValue AI_TERRAIN;
    public static final ModConfigSpec.BooleanValue PROJECTILE_CHUNK_LOAD;
    public static final ModConfigSpec.IntValue PROJECTILE_CHUNK_LEASE_TICKS;
    public static final ModConfigSpec.IntValue LAG_COMPENSATION_MAX_MS;
    public static final ModConfigSpec.IntValue ANTI_CHEAT_THRESHOLD;
    public static final ModConfigSpec.EnumValue<io.github.mcheliceport.combat.AntiCheat.Action> ANTI_CHEAT_ACTION;
    public static final ModConfigSpec.BooleanValue ENABLE_COMMAND;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> COMMAND_PERMISSIONS;
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_BOUNDING_BOX;
    public static final ModConfigSpec.BooleanValue LONG_DISTANCE_TRACKING;
    public static final ModConfigSpec.BooleanValue PLAYERS_CHOOSE_TEAM;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("vehicles");
        SERVER_AUTHORITATIVE_MOVEMENT = builder
                .comment("false: the pilot's client moves the vehicle it controls and the server checks the movement.",
                        "true: clients only send control inputs and the server simulates every vehicle, as upstream McHeli CE does.",
                        "Takes effect for vehicles after a world restart.")
                .define("serverAuthoritativeMovement", false);
        builder.pop();

        builder.comment("Rotation limits pilots fly with (upstream's MCH_ServerSettings, sent to every client).")
                .push("rotationLimits");
        ENABLE_ROTATION_LIMIT = builder
                .comment("Pull helicopters back towards the limits below. Upstream: EnableRotationLimit.")
                .define("enableRotationLimit", false);
        PITCH_LIMIT_MAX = builder
                .comment("Nose-down pitch limit in degrees. Upstream: PitchLimitMax.")
                .defineInRange("pitchLimitMax", 10, 0, 80);
        PITCH_LIMIT_MIN = builder
                .comment("Nose-up pitch limit in degrees (negative). Upstream: PitchLimitMin.")
                .defineInRange("pitchLimitMin", -10, -80, 0);
        ROLL_LIMIT = builder
                .comment("Roll limit in degrees either way. Upstream: RollLimit.")
                .defineInRange("rollLimit", 35, 0, 80);
        builder.pop();

        builder.push("camera");
        ENABLE_CAM_DIST_CHANGE = builder
                .comment("Let riders change the third-person camera distance with Page Up and Page Down.",
                        "Upstream: enableCamDistChange.")
                .define("enableCamDistChange", true);
        builder.pop();

        builder.push("uav");
        UAV_CONTROL_RANGE = builder.comment("Maximum station/tablet control distance in blocks.")
                .defineInRange("controlRange", 5000, 24, 5000);
        UAV_REMOTE_VIEW_DISTANCE = builder.comment("Camera chunk radius during UAV control, also bounded by player/server view distance.")
                .defineInRange("remoteViewDistance", 4, 2, 8);
        MAX_PAIRED_UAVS = builder.comment("Maximum paired UAVs per controller. Upstream: MaxPairedUavPerStation.")
                .defineInRange("maxPairedUavs", 5, 0, 64);
        TABLET_NORMAL_UAVS = builder.comment("Allow tablets to bind/control full-size UAVs. Default: small UAVs only.")
                .define("tabletControlNormalUav", false);
        builder.pop();

        builder.comment("Spotting with the rangefinder and targeting pods (upstream's lines of the same names in mcheli.cfg).")
                .push("spotting");
        ENABLE_ENTITY_MARKER = builder
                .comment("Let clients draw markers over spotted entities and teammates. Clients can still turn",
                        "their own markers off. Upstream: the server's DisplayEntityMarker.")
                .define("enableEntityMarker", true);
        RANGE_FINDER_SPOT_DIST = builder
                .comment("How far the rangefinder spots, in blocks. Upstream: RangeFinderSpotDist.")
                .defineInRange("rangeFinderSpotDist", 400, 1, 10000);
        RANGE_FINDER_SPOT_TIME = builder
                .comment("Seconds a player or vehicle the rangefinder spotted stays marked (mobs stay 60). Upstream: RangeFinderSpotTime.")
                .defineInRange("rangeFinderSpotTime", 15, 1, 3600);
        RANGE_FINDER_CONSUME = builder
                .comment("Each spot uses one charge of the rangefinder. Upstream: RangeFinderConsume.")
                .define("rangeFinderConsume", true);
        builder.pop();

        builder.comment("The hand-held Stinger and Javelin.").push("lightWeapons");
        STINGER_LOCK_RANGE = builder
                .comment("How far the hand-held Stinger and Javelin lock on, in blocks. Upstream: StingerLockRange.")
                .defineInRange("stingerLockRange", 320.0, 1.0, 10000.0);
        builder.pop();

        builder.comment("The aircraft charger, which feeds energy to nearby electric vehicles.").push("charger");
        CHARGER_RANGE = builder
                .comment("How far the charger reaches, in blocks around it; 0 turns it off. Upstream: ChargerRange.")
                .defineInRange("chargerRange", 6.0, 0.0, 256.0);
        CHARGER_TRANSFER_RATE = builder
                .comment("Energy the charger gives out every 10 ticks, shared by the vehicles in range. Upstream: ChargerTransferRate.")
                .defineInRange("chargerTransferRate", 256, 0, Integer.MAX_VALUE);
        CHARGER_CAPACITY = builder
                .comment("Energy the charger holds. Upstream: ChargerCapacity.")
                .defineInRange("chargerCapacity", 100000, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("M10c ground crew: repair pads and supply vehicles with a stock (a port addition).")
                .push("groundCrew");
        GROUND_CREW = builder
                .comment("Repair pads service vehicles and stocked supply vehicles restock on them.")
                .define("enabled", true);
        PAD_REPAIR_HP = builder
                .comment("Hit points a repair pad restores each second to a vehicle standing still on it.")
                .defineInRange("padRepairHp", 10, 0, 100000);
        PAD_SUPPLY_RANGE = builder
                .comment("How close to a pad's centre a fuel or ammo supply vehicle must be to top up the vehicle on it, in blocks.")
                .defineInRange("padSupplyRange", 8.0, 0.0, 64.0);
        builder.pop();

        builder.comment("The wingman formation (upstream's [formation] section of its wingman config). These are read",
                        "when the server starts; the wingman panel and /wingman change them until it stops, unsaved.")
                .push("wingman");
        WINGMAN_SIDE_DIST = builder
                .comment("Lateral distance from the leader's centre line, in blocks. Upstream: formationSideDist.")
                .defineInRange("formationSideDist", 20.0, 1.0, 500.0);
        WINGMAN_ALT_OFFSET = builder
                .comment("Altitude offset from the leader, in blocks; positive is above. Upstream: formationAltOffset.")
                .defineInRange("formationAltOffset", 0.0, -500.0, 500.0);
        WINGMAN_REAR_DIST = builder
                .comment("Distance behind the leader, in blocks. Upstream: formationRearDist.")
                .defineInRange("formationRearDist", 30.0, 1.0, 500.0);
        WINGMAN_MAX_WINGMEN = builder
                .comment("Wingmen per aircraft. Upstream: maxWingmen.")
                .defineInRange("maxWingmen", 10, 1, 64);
        WINGMAN_MIN_ATTACK_ALTITUDE = builder
                .comment("Lowest absolute Y of attack runs, whatever the weapon; 0 is no floor. Upstream: minAttackAltitude.")
                .defineInRange("minAttackAltitude", 0.0, 0.0, 10000.0);
        WINGMAN_MAX_ATTACK_ALTITUDE = builder
                .comment("Highest absolute Y of attack runs, whatever the weapon; 0 is no ceiling. Upstream: maxAttackAltitude.")
                .defineInRange("maxAttackAltitude", 0.0, 0.0, 10000.0);
        builder.pop();

        builder.comment("Battles between NPCs of different teams, a port addition: vs-player gunners, wingmen and",
                        "aircraft on missions also attack the gunners and unmanned aircraft of other teams.")
                .push("npcBattles");
        NPC_BATTLES = builder
                .comment("false: NPCs only attack what upstream's did (monsters, players, target drones, team gunners).")
                .define("enabled", true);
        builder.pop();

        builder.comment("M10d AI pilots of wingmen and aircraft on missions (a port addition).").push("ai");
        AI_DIFFICULTY = builder
                .comment("EASY looks 2 seconds ahead for terrain and reacts to missiles in 1.5 seconds; NORMAL looks",
                        "2 and 4 seconds ahead, reacts in 0.6 seconds and turns away from the missile; VETERAN looks",
                        "up to 8 seconds ahead, reacts in 0.2 seconds and flies attack runs 20 to 40 blocks up.")
                .defineEnum("difficulty", AiSkill.NORMAL);
        AI_TERRAIN = builder
                .comment("AI pilots climb over terrain they see ahead. false: upstream's flight, which can fly into hills.")
                .define("terrainAvoidance", true);
        builder.pop();

        builder.comment("Keeping chunks loaded along the flight path of projectiles, so bullets, rockets and missiles",
                        "keep flying and can hit targets where no player keeps the terrain loaded.")
                .push("projectiles");
        PROJECTILE_CHUNK_LOAD = builder
                .comment("true: every projectile loads the chunks ahead of it. false: only the weapons whose content",
                        "sets CanLoadChunks do. Can add server load while many projectiles fly. Upstream: ProjectileChunkLoad.")
                .define("chunkLoad", false);
        PROJECTILE_CHUNK_LEASE_TICKS = builder
                .comment("Ticks a chunk stays loaded after the last projectile asked for it, so the shots of a burst",
                        "keep the same chunks loaded (20 ticks = 1 second). Upstream: ProjectileChunkLeaseTicks.")
                .defineInRange("chunkLeaseTicks", 40, 0, 1200);
        LAG_COMPENSATION_MAX_MS = builder
                .comment("Lag compensation (a port addition): a player's shots test vehicles where that player saw them,",
                        "up to this many milliseconds in the past (round trip plus interpolation). Higher favours shooters",
                        "on slow connections, lower favours their targets. 0 turns it off.")
                .defineInRange("lagCompensationMaxMs", 250, 0, 500);
        builder.pop();

        builder.comment("Server checks on weapon fire (a port addition). A failed check always drops that shot or lock;",
                        "these settings decide what happens to a player who keeps failing them.")
                .push("antiCheat");
        ANTI_CHEAT_THRESHOLD = builder
                .comment("Violation score that triggers the action below. A failure adds 1 or 2 and the score decays",
                        "with a one-minute time constant, so honest players with lag stay well below it. 0 turns alerts off.")
                .defineInRange("threshold", 20, 0, 1000);
        ANTI_CHEAT_ACTION = builder
                .comment("LOG: write a warning to the server log. NOTIFY_OPS: also tell online operators.",
                        "KICK: also disconnect the player. At most one action per player per minute.")
                .defineEnum("action", io.github.mcheliceport.combat.AntiCheat.Action.LOG);
        builder.pop();

        builder.comment("The /mcheli command.").push("commands");
        ENABLE_COMMAND = builder
                .comment("false: there is no /mcheli command. Upstream: EnableCommand.")
                .define("enabled", true);
        COMMAND_PERMISSIONS = builder
                .comment("Players who may use a /mcheli subcommand without operator permission, one entry per",
                        "subcommand, such as \"title: Alice, Bob\". Upstream: CommandPermission.")
                .defineListAllowEmpty("permissions", List.of(), () -> "", o -> o instanceof String);
        ENABLE_DEBUG_BOUNDING_BOX = builder
                .comment("false: clients cannot show entity hitboxes (F3 + B). /mcheli showboundingbox sets it.",
                        "Upstream: EnableDebugBoundingBox.")
                .define("enableDebugBoundingBox", true);
        builder.pop();

        builder.push("tracking");
        LONG_DISTANCE_TRACKING = builder
                .comment("true: players see McHeli vehicles up to their tracking range (1,200 blocks for aircraft, tanks",
                        "and ships), past the server's view distance and in chunks the player has not loaded.",
                        "false: vanilla's limit, the view distance. Upstream: ExperimentalRemoveClientTrackingRestrictions.")
                .define("longDistance", true);
        builder.pop();

        builder.comment("The multiplay manager (M), a port addition.").push("teams");
        PLAYERS_CHOOSE_TEAM = builder
                .comment("true: every player can join a team, or leave theirs, with the manager's team buttons.",
                        "false: only game masters can, as with /team join. Upstream had no such buttons.")
                .define("playersChooseTeam", true);
        builder.pop();
        SPEC = builder.build();
    }

    /** The value, or its default before a server has loaded the config (unit tests, the main menu). */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private ServerConfig() {}
}
