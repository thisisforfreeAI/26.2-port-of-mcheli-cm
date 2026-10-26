package io.github.mcheliceport.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.emitter.YamlDump;
import io.github.mcheliceport.net.CommandRequestPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.TitlePayload;
import io.github.mcheliceport.wingman.config.WingmanConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.Nullable;

/**
 * The {@code /mcheli} command (upstream {@code MCH_Command}), with Brigadier:
 * <ul>
 *   <li>{@code list}: the subcommands.</li>
 *   <li>{@code sendss <player>}: saves the player's screen to {@code <server folder>/screenshots_op},
 *       if their client allows it ({@code privacy.allowScreenCapture}).</li>
 *   <li>{@code modlist <players>}: shows the sender each player's mods, if their client shares them
 *       (its {@code debugLog} is on, as upstream).</li>
 *   <li>{@code status entity|tile [min]}: how many loaded entities or block entities of each class
 *       the sender's level holds, for the classes with at least {@code min}.</li>
 *   <li>{@code killentity|removeentity <name>}, {@code attackentity <name> <damage> [source]}: kill,
 *       remove or hurt every loaded entity but players whose class name contains {@code name}, or,
 *       a port addition, whose entity type ID does.</li>
 *   <li>{@code fill <from> <to> <block> [replace|destroy|keep|override]}: upstream's fill, up to
 *       3,000,000 blocks, which empties containers before replacing them.</li>
 *   <li>{@code title <seconds> <position> <message>}: shows every player a message (TitlePayload).</li>
 *   <li>{@code delayhitbox [ticks]}, {@code showboundingbox true|false}: set CommonConfig's
 *       {@code hitBoxDelayTick} and the server config's {@code enableDebugBoundingBox}.</li>
 *   <li>{@code reconfig}: sends every player the server settings again (KNOWN_ISSUES 102).</li>
 *   <li>{@code dumpyaml [folder]}: writes all content that was not loaded from YAML (that is, TXT
 *       content) as YAML files into {@code <game dir>/<folder>}, by default {@code mcheli_yaml_dump}.
 *       Upstream accepted any path; the port only takes a folder name, so operators cannot write
 *       outside the game folder.</li>
 * </ul>
 * As upstream, a subcommand needs operator permission (level 2) or the sender's name in the server
 * config's {@code commands.permissions} entry for it, and {@code commands.enabled} turns the whole
 * command off.
 */
@EventBusSubscriber(modid = McheliCePort.MOD_ID)
public final class McheliCommand {

    public static final String DEFAULT_DUMP_FOLDER = "mcheli_yaml_dump";
    /** Upstream's {@code ALL_COMMAND} order. */
    public static final List<String> SUBCOMMANDS = List.of("sendss", "modlist", "reconfig", "title", "fill", "status", "killentity",
            "removeentity", "attackentity", "showboundingbox", "list", "delayhitbox", "dumpyaml", "anticheat");
    public static final int MAX_FILL = 3_000_000;
    static final List<String> DAMAGE_SOURCES = List.of("player", "inFire", "onFire", "lava", "inWall", "drown",
            "starve", "cactus", "fall", "outOfWorld", "generic", "magic", "wither", "anvil", "fallingBlock");
    private static final List<String> FILL_MODES = List.of("replace", "destroy", "keep", "override");
    private static final Pattern FOLDER_NAME = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final SimpleCommandExceptionType FILL_FAILED =
            new SimpleCommandExceptionType(Component.translatable("commands.fill.failed"));
    private static final SimpleCommandExceptionType ATTACK_USAGE = new SimpleCommandExceptionType(Component.literal(
            "/mcheli attackentity <entity class name or type ID> <damage> [damage source]"));
    private static final SimpleCommandExceptionType OUT_OF_WORLD =
            new SimpleCommandExceptionType(Component.translatable("argument.pos.outofworld"));

    private McheliCommand() {
    }

    @SubscribeEvent
    static void registerCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher(), event.getBuildContext());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("mcheli")
                .requires(source -> SUBCOMMANDS.stream().anyMatch(name -> canUse(source, name)))
                .then(sub("list").executes(McheliCommand::list))
                .then(sub("sendss").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> requestFrom(c, List.of(EntityArgument.getPlayer(c, "player")),
                                CommandRequestPayload.Action.SCREENSHOT))))
                .then(sub("modlist").then(Commands.argument("players", EntityArgument.players())
                        .executes(c -> requestFrom(c, EntityArgument.getPlayers(c, "players"),
                                CommandRequestPayload.Action.MOD_LIST))))
                .then(sub("status")
                        .then(Commands.literal("entity")
                                .executes(c -> statusEntities(c, 0))
                                .then(Commands.argument("min", IntegerArgumentType.integer())
                                        .executes(c -> statusEntities(c, IntegerArgumentType.getInteger(c, "min")))))
                        .then(Commands.literal("tile")
                                .executes(c -> statusTiles(c, 0))
                                .then(Commands.argument("min", IntegerArgumentType.integer())
                                        .executes(c -> statusTiles(c, IntegerArgumentType.getInteger(c, "min"))))))
                .then(sub("killentity").then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(c -> removeEntities(c, true))))
                .then(sub("removeentity").then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(c -> removeEntities(c, false))))
                .then(sub("attackentity").then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests(McheliCommand::suggestDamageSource)
                        .executes(McheliCommand::attackEntities)))
                .then(sub("fill").then(Commands.argument("from", BlockPosArgument.blockPos())
                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                .then(Commands.argument("block", BlockStateArgument.block(context))
                                        .executes(c -> fill(c, "replace"))
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(FILL_MODES, b))
                                                .executes(c -> fill(c, StringArgumentType.getString(c, "mode"))))))))
                .then(sub("title").then(Commands.argument("seconds", IntegerArgumentType.integer())
                        .then(Commands.argument("position", IntegerArgumentType.integer())
                                .then(Commands.argument("message", ComponentArgument.textComponent(context))
                                        .executes(McheliCommand::title)))))
                .then(sub("delayhitbox")
                        .executes(McheliCommand::showHitBoxDelay)
                        .then(Commands.argument("ticks", IntegerArgumentType.integer())
                                .executes(McheliCommand::setHitBoxDelay)))
                .then(sub("showboundingbox").then(Commands.argument("enable", BoolArgumentType.bool())
                        .executes(McheliCommand::showBoundingBox)))
                .then(sub("anticheat")
                        .executes(c -> antiCheat(c, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> antiCheat(c, EntityArgument.getPlayer(c, "player")))))
                .then(sub("reconfig").executes(McheliCommand::reconfig))
                .then(sub("dumpyaml")
                        .executes(c -> dumpYaml(c, DEFAULT_DUMP_FOLDER))
                        .then(Commands.argument("folder", StringArgumentType.word())
                                .executes(c -> dumpYaml(c, StringArgumentType.getString(c, "folder"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sub(String name) {
        return Commands.literal(name).requires(source -> canUse(source, name));
    }

    /**
     * Upstream {@code checkCommandPermission}: an operator (level 2), or a player the server config's
     * {@code commands.permissions} lists for the subcommand; nobody while {@code commands.enabled} is off.
     */
    public static boolean canUse(CommandSourceStack source, String subcommand) {
        if (ServerConfig.SPEC.isLoaded() && !ServerConfig.ENABLE_COMMAND.get()) {
            return false;
        }
        if (Commands.LEVEL_GAMEMASTERS.check(source.permissions())) {
            return true;
        }
        ServerPlayer player = source.getPlayer();
        return player != null && ServerConfig.SPEC.isLoaded()
                && isListed(ServerConfig.COMMAND_PERMISSIONS.get(), subcommand, player.getGameProfile().name());
    }

    /** Upstream {@code CommandPermission}: entries {@code "name: player, player"}, names in any case. */
    static boolean isListed(List<? extends String> entries, String subcommand, String playerName) {
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length != 2 || !parts[0].trim().equalsIgnoreCase(subcommand)) {
                continue;
            }
            for (String name : parts[1].trim().split("\\s*,\\s*")) {
                if (name.equalsIgnoreCase(playerName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        c.getSource().sendSuccess(() -> Component.literal("/mcheli command list : " + String.join(", ", SUBCOMMANDS)),
                false);
        return SUBCOMMANDS.size();
    }

    /**
     * Upstream {@code handleSendScreenshot} and {@code handleModList}: asks each player's client for a
     * screenshot or its mod list ({@link ClientRequests}), and returns how many were asked.
     */
    private static int requestFrom(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players,
            CommandRequestPayload.Action action) {
        CommandSourceStack source = c.getSource();
        int asked = 0;
        for (ServerPlayer player : players) {
            String name = player.getGameProfile().name();
            if (player.connection == null || !player.connection.hasChannel(CommandRequestPayload.TYPE)) {
                source.sendFailure(Component.literal(name + " does not have the mod"));
                continue;
            }
            ClientRequests.request(source.getPlayer(), player, action);
            asked++;
            String what = action == CommandRequestPayload.Action.SCREENSHOT ? "a screenshot" : "the mod list";
            source.sendSuccess(() -> Component.literal("Asked " + name + " for " + what), false);
        }
        return asked;
    }

    /**
     * M9: the anti-cheat violation scores of online players, or one player's counts by check and
     * latest failures. Returns how many players (or failures) were listed.
     */
    private static int antiCheat(CommandContext<CommandSourceStack> c, @Nullable ServerPlayer target) {
        CommandSourceStack source = c.getSource();
        long now = source.getLevel().getGameTime();
        if (target == null) {
            int shown = 0;
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                var r = io.github.mcheliceport.combat.AntiCheat.recordOf(player.getUUID());
                if (r != null && !r.counts().isEmpty()) {
                    String line = String.format(" %s : score %.1f %s", player.getGameProfile().name(), r.score(now), r.counts());
                    source.sendSuccess(() -> Component.literal(line), false);
                    shown++;
                }
            }
            if (shown == 0) {
                source.sendSuccess(() -> Component.literal("No anti-cheat violations"), false);
            }
            return shown;
        }
        var r = io.github.mcheliceport.combat.AntiCheat.recordOf(target.getUUID());
        String name = target.getGameProfile().name();
        if (r == null || r.counts().isEmpty()) {
            source.sendSuccess(() -> Component.literal(name + " has no anti-cheat violations"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format("--- %s : score %.1f %s ---", name, r.score(now),
                r.counts())), false);
        var recent = r.recent();
        for (var e : recent) {
            String line = String.format(" %ds ago: %s, %s", (now - e.tick()) / 20, e.check(), e.detail());
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return recent.size();
    }

    private static int statusEntities(CommandContext<CommandSourceStack> c, int min) {
        List<Object> entities = new ArrayList<>();
        c.getSource().getLevel().getAllEntities().forEach(entities::add);
        return status(c.getSource(), "Server loaded Entity List", entities, min);
    }

    private static int statusTiles(CommandContext<CommandSourceStack> c, int min) {
        List<Object> tiles = new ArrayList<>();
        c.getSource().getLevel().getChunkSource().chunkMap.forEachReadyToSendChunk(
                chunk -> tiles.addAll(chunk.getBlockEntities().values()));
        return status(c.getSource(), "Server loaded Tile Entity List", tiles, min);
    }

    /** Upstream {@code executeStatusSub}: counts by class name, sorted, also written to the log. */
    static int status(CommandSourceStack source, String title, List<?> objects, int min) {
        Map<String, Integer> counts = countByClass(objects);
        source.sendSuccess(() -> Component.literal("--- " + title + " ---"), false);
        int shown = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() >= min) {
                String line = " " + e.getKey() + " : " + e.getValue();
                McheliCePort.LOGGER.info(line);
                source.sendSuccess(() -> Component.literal(line), false);
                shown++;
            }
        }
        if (shown == 0) {
            McheliCePort.LOGGER.info("none");
            source.sendSuccess(() -> Component.literal("none"), false);
        }
        return shown;
    }

    static Map<String, Integer> countByClass(List<?> objects) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Object o : objects) {
            counts.merge(o.getClass().getName(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Whether a command's {@code name} picks the entity: upstream matched part of the Java class
     * name in any case; the port also matches part of the entity type ID, such as {@code minecraft:bat}.
     */
    static boolean matches(Entity entity, String name) {
        if (entity instanceof Player) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return entity.getClass().getName().toLowerCase(Locale.ROOT).contains(lower)
                || BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().contains(lower);
    }

    private static List<Entity> matching(ServerLevel level, String name) {
        List<Entity> found = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity != null && matches(entity, name)) {
                found.add(entity);
            }
        }
        return found;
    }

    /**
     * Upstream {@code executeKillEntity} called {@code setDead}, which an entity could override, and
     * {@code executeRemoveEntity} set {@code isDead} directly: here {@link Entity#discard} and
     * {@link Entity#setRemoved}.
     */
    private static int removeEntities(CommandContext<CommandSourceStack> c, boolean kill) {
        String name = StringArgumentType.getString(c, "name");
        List<Entity> found = matching(c.getSource().getLevel(), name);
        for (Entity entity : found) {
            if (kill) {
                entity.discard();
            } else {
                entity.setRemoved(Entity.RemovalReason.DISCARDED);
            }
        }
        int n = found.size();
        c.getSource().sendSuccess(() -> Component.literal(n + " entity " + (kill ? "killed" : "removed")
                + "(" + name + ")."), true);
        return n;
    }

    /**
     * Upstream's {@code attackentity <name> <damage> [source]}, read from one argument so that a name
     * may be a type ID such as {@code minecraft:bat}, which Brigadier's words cannot hold.
     */
    private static int attackEntities(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String[] args = StringArgumentType.getString(c, "args").trim().split("\\s+");
        float damage;
        try {
            damage = args.length >= 2 && args.length <= 3 ? Float.parseFloat(args[1]) : Float.NaN;
        } catch (NumberFormatException e) {
            damage = Float.NaN;
        }
        if (Float.isNaN(damage)) {
            throw ATTACK_USAGE.create();
        }
        String name = args[0];
        ServerLevel level = c.getSource().getLevel();
        DamageSource source = damageSource(level.damageSources(), args.length == 3 ? args[2] : "",
                c.getSource().getPlayer());
        List<Entity> found = matching(level, name);
        for (Entity entity : found) {
            entity.hurtServer(level, source, damage);
        }
        int n = found.size();
        float d = damage;
        c.getSource().sendSuccess(() -> Component.literal(n + " entity attacked(" + name + ", damage=" + d + ")."),
                true);
        return n;
    }

    /** Upstream's tab completion: the damage sources after a name and a damage. */
    private static CompletableFuture<Suggestions> suggestDamageSource(
            CommandContext<CommandSourceStack> c, SuggestionsBuilder builder) {
        String input = builder.getRemaining();
        String[] args = input.split("\\s+", -1);
        if (args.length != 3) {
            return builder.buildFuture();
        }
        SuggestionsBuilder last =
                builder.createOffset(builder.getStart() + input.lastIndexOf(args[2]));
        return SharedSuggestionProvider.suggest(DAMAGE_SOURCES, last);
    }

    /**
     * Upstream's damage sources by name, in any case; anything else is generic. Upstream suggested
     * {@code outOfWorld} but gave generic damage for it; here it is the void's damage.
     */
    static DamageSource damageSource(DamageSources sources, String name, @Nullable ServerPlayer sender) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "player" -> sender != null ? sources.playerAttack(sender) : sources.generic();
            case "anvil" -> sources.source(DamageTypes.FALLING_ANVIL);
            case "cactus" -> sources.cactus();
            case "drown" -> sources.drown();
            case "fall" -> sources.fall();
            case "fallingblock" -> sources.source(DamageTypes.FALLING_BLOCK);
            case "infire" -> sources.inFire();
            case "inwall" -> sources.inWall();
            case "lava" -> sources.lava();
            case "magic" -> sources.magic();
            case "onfire" -> sources.onFire();
            case "outofworld" -> sources.fellOutOfWorld();
            case "starve" -> sources.starve();
            case "wither" -> sources.wither();
            default -> sources.generic();
        };
    }

    /**
     * Upstream {@code executeFill}: {@code keep} leaves solid blocks, {@code override} leaves air,
     * {@code destroy} breaks the old block first without drops; containers are emptied first so
     * they drop nothing. Unloaded blocks are skipped.
     */
    private static int fill(CommandContext<CommandSourceStack> c, String mode) throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        ServerLevel level = source.getLevel();
        BlockPos a = BlockPosArgument.getBlockPos(c, "from");
        BlockPos b = BlockPosArgument.getBlockPos(c, "to");
        BlockInput block = BlockStateArgument.getBlock(c, "block");
        BlockPos min = BlockPos.min(a, b);
        BlockPos max = BlockPos.max(a, b);
        if (level.isOutsideBuildHeight(min.getY()) || level.isOutsideBuildHeight(max.getY())) {
            throw OUT_OF_WORLD.create();
        }
        long blocks = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        if (blocks > MAX_FILL) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.fill.toobig", MAX_FILL, blocks))
                    .create();
        }
        boolean keep = mode.equals("keep");
        boolean destroy = mode.equals("destroy");
        boolean override = mode.equals("override");
        int changed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(pos) || (level.isEmptyBlock(pos) ? override : keep)) {
                continue;
            }
            if (destroy) {
                level.destroyBlock(pos, false);
            }
            BlockEntity old = level.getBlockEntity(pos);
            if (old instanceof Clearable clearable) {
                clearable.clearContent();
            }
            if (block.place(level, pos, Block.UPDATE_ALL)) {
                changed++;
            }
        }
        if (changed == 0) {
            throw FILL_FAILED.create();
        }
        int n = changed;
        source.sendSuccess(() -> Component.translatable("commands.fill.success", n), true);
        return n;
    }

    /** Upstream {@code handleTitle}: 1 to 180 seconds, positions 0 to 5, sent to every player. */
    private static int title(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int seconds = Math.clamp(IntegerArgumentType.getInteger(c, "seconds"), 1, 180);
        int position = Math.clamp(IntegerArgumentType.getInteger(c, "position"), 0, 5);
        Component message = ComponentArgument.getResolvedComponent(c, "message");
        ModNetwork.sendToAllPlayers(c.getSource().getServer(), new TitlePayload(message, 20 * seconds, position));
        return 1;
    }

    private static int showHitBoxDelay(CommandContext<CommandSourceStack> c) {
        int ticks = CommonConfig.HIT_BOX_DELAY_TICK.get();
        c.getSource().sendSuccess(() -> Component.literal("Current delay of hitbox = " + ticks + " [0 ~ 50]"), false);
        return ticks;
    }

    /** Upstream {@code handleDelayHitbox}: at most 50, saved to the config file. */
    private static int setHitBoxDelay(CommandContext<CommandSourceStack> c) {
        int ticks = Math.clamp(IntegerArgumentType.getInteger(c, "ticks"), -1000, 50);
        CommonConfig.HIT_BOX_DELAY_TICK.set(ticks);
        CommonConfig.HIT_BOX_DELAY_TICK.save();
        return showHitBoxDelay(c);
    }

    /** Upstream {@code handleShowBoundingBox}: saved and sent to every player. */
    private static int showBoundingBox(CommandContext<CommandSourceStack> c) {
        boolean enable = BoolArgumentType.getBool(c, "enable");
        ServerConfig.ENABLE_DEBUG_BOUNDING_BOX.set(enable);
        ServerConfig.ENABLE_DEBUG_BOUNDING_BOX.save();
        ModNetwork.sendServerSettings(c.getSource().getServer());
        c.getSource().sendSuccess(() -> Component.literal(enable ? "Enabled bounding box [F3 + b]"
                : "Disabled bounding box"), true);
        return enable ? 1 : 0;
    }

    /**
     * Upstream {@code handleReconfig} read its config file again. NeoForge already reloads a config
     * file when it is saved, so this applies the wingman settings and sends the server settings again.
     */
    private static int reconfig(CommandContext<CommandSourceStack> c) {
        WingmanConfig.loadFromServerConfig();
        ModNetwork.sendServerSettings(c.getSource().getServer());
        String message = c.getSource().getServer().isDedicatedServer() ? "Reload server side mcheli config"
                : "Reload mcheli config";
        c.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int dumpYaml(CommandContext<CommandSourceStack> context, String folder) {
        CommandSourceStack source = context.getSource();
        if (!isSafeFolderName(folder)) {
            source.sendFailure(Component.literal("The output folder must be a plain folder name, such as "
                    + DEFAULT_DUMP_FOLDER));
            return 0;
        }
        Path base = FMLPaths.GAMEDIR.get().resolve(folder);
        try {
            YamlDump.Result result = YamlDump.dump(ContentRegistries.get(), base);
            String message = "Dumped " + result.written() + " YAML files to " + base.toAbsolutePath()
                    + (result.failed() > 0 ? " (" + result.failed() + " failed, see the log)" : "");
            source.sendSuccess(() -> Component.literal(message), true);
            return result.written();
        } catch (IOException e) {
            source.sendFailure(Component.literal("Failed to dump YAML: " + e.getMessage()));
            return 0;
        }
    }

    static boolean isSafeFolderName(String folder) {
        return FOLDER_NAME.matcher(folder).matches() && !folder.matches("\\.+");
    }
}
