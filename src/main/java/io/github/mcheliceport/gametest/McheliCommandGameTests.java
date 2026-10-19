package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.mcheliceport.command.ClientRequests;
import io.github.mcheliceport.command.ClientRequests.Result;
import io.github.mcheliceport.command.McheliCommand;
import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.config.ServerSettings;
import io.github.mcheliceport.net.CommandRequestPayload.Action;
import io.github.mcheliceport.net.ModListPayload;
import io.github.mcheliceport.net.ScreenshotChunkPayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

/** The {@code /mcheli} subcommands ported in M7. */
final class McheliCommandGameTests {

    private McheliCommandGameTests() {
    }

    static void register() {
        ModGameTests.testAlone("mcheli_command_permissions_and_settings", 20, McheliCommandGameTests::settings);
        ModGameTests.testAlone("mcheli_command_entities_and_fill", 20, McheliCommandGameTests::entities);
        ModGameTests.testAlone("mcheli_command_client_requests", 20, McheliCommandGameTests::clientRequests);
    }

    private static int run(CommandSourceStack source, String command) throws CommandSyntaxException {
        return source.getServer().getCommands().getDispatcher().execute(command, source);
    }

    private static boolean refused(CommandSourceStack source, String command) {
        try {
            run(source, command);
            return false;
        } catch (CommandSyntaxException e) {
            return true;
        }
    }

    /**
     * Only operators and the players the config lists for a subcommand may use it, and nobody while
     * the command is off; {@code delayhitbox} and {@code showboundingbox} change their settings,
     * {@code title}, {@code reconfig} and {@code list} run.
     */
    private static void settings(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        CommandSourceStack user = player.createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS)
                .withSuppressedOutput();
        CommandSourceStack op = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER)
                .withSuppressedOutput();
        List<? extends String> permissions = ServerConfig.COMMAND_PERMISSIONS.get();
        int delay = CommonConfig.HIT_BOX_DELAY_TICK.get();
        try {
            check(h, refused(user, "mcheli list"), "a player who is not an operator ran /mcheli list");
            ServerConfig.COMMAND_PERMISSIONS.set(List.of("LIST : someone, " + player.getGameProfile().name()));
            check(h, !refused(user, "mcheli list") && refused(user, "mcheli status entity"),
                    "the permission list did not allow exactly its subcommand");
            ServerConfig.ENABLE_COMMAND.set(false);
            check(h, refused(op, "mcheli list"), "/mcheli ran while it is off");
            ServerConfig.ENABLE_COMMAND.set(true);
            ServerConfig.COMMAND_PERMISSIONS.set(permissions);
            check(h, run(op, "mcheli list") == McheliCommand.SUBCOMMANDS.size(), "list did not run");

            run(op, "mcheli delayhitbox 70");
            check(h, CommonConfig.HIT_BOX_DELAY_TICK.get() == 50, "delayhitbox did not cap the delay at 50");
            run(op, "mcheli delayhitbox 7");
            check(h, CommonConfig.HIT_BOX_DELAY_TICK.get() == 7 && run(op, "mcheli delayhitbox") == 7,
                    "delayhitbox did not set the delay");

            check(h, run(op, "mcheli showboundingbox false") == 0 && !ServerSettings.server().enableDebugBoundingBox(),
                    "showboundingbox false did not forbid hitboxes");
            check(h, run(op, "mcheli showboundingbox true") == 1 && ServerSettings.server().enableDebugBoundingBox(),
                    "showboundingbox true did not allow hitboxes");

            check(h, run(op, "mcheli title 3 2 {\"text\":\"Mission start\",\"color\":\"red\"}") == 1
                    && run(op, "mcheli title 500 9 \"plain\"") == 1, "title did not run");
            check(h, refused(op, "mcheli title 3 2 {\"text\":"), "title took a broken message");
            check(h, run(op, "mcheli reconfig") == 1, "reconfig did not run");
        } catch (CommandSyntaxException e) {
            throw h.assertionException(net.minecraft.network.chat.Component.literal(e.getMessage()));
        } finally {
            // delayhitbox and showboundingbox save the config files; save them back as they were.
            ServerConfig.COMMAND_PERMISSIONS.set(permissions);
            ServerConfig.ENABLE_COMMAND.set(true);
            ServerConfig.ENABLE_DEBUG_BOUNDING_BOX.set(true);
            ServerConfig.SPEC.save();
            CommonConfig.HIT_BOX_DELAY_TICK.set(delay);
            CommonConfig.SPEC.save();
        }
        h.succeed();
    }

    /**
     * {@code status} counts the level's entities and block entities, {@code attackentity},
     * {@code killentity} and {@code removeentity} pick entities by class name or type ID and never
     * players, and {@code fill} honours keep, override and destroy and empties containers without drops.
     */
    private static void entities(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        CommandSourceStack op = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER)
                .withSuppressedOutput();
        try {
            MushroomCow cow = h.spawn(EntityTypes.MOOSHROOM, new BlockPos(1, 2, 1));
            float health = cow.getHealth();
            check(h, run(op, "mcheli attackentity minecraft:mooshroom 4 magic") == 1
                    && Math.abs(cow.getHealth() - (health - 4.0F)) < 0.01F, "attackentity did not hurt the mooshroom");
            check(h, run(op, "mcheli status entity") > 0 && run(op, "mcheli status entity 100000") == 0,
                    "status entity did not count the entities");

            Allay a = h.spawn(EntityTypes.ALLAY, new BlockPos(1, 3, 1));
            Allay b = h.spawn(EntityTypes.ALLAY, new BlockPos(2, 3, 1));
            check(h, run(op, "mcheli killentity allay") == 2 && a.isRemoved() && b.isRemoved(),
                    "killentity did not kill the allays by class name");
            Allay c = h.spawn(EntityTypes.ALLAY, new BlockPos(1, 3, 2));
            check(h, run(op, "mcheli removeentity minecraft:allay") == 1 && c.isRemoved(),
                    "removeentity did not remove the allay by type ID");
            check(h, run(op, "mcheli killentity ServerPlayer") == 0 && !player.isRemoved(),
                    "killentity picked a player");

            BlockPos from = h.absolutePos(new BlockPos(0, 4, 0));
            BlockPos to = h.absolutePos(new BlockPos(2, 4, 2));
            BlockPos chestPos = h.absolutePos(new BlockPos(1, 4, 1));
            level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
            if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
                chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
            }
            String area = " " + from.getX() + " " + from.getY() + " " + from.getZ() + " " + to.getX() + " " + to.getY()
                    + " " + to.getZ() + " ";
            check(h, run(op, "mcheli fill" + area + "minecraft:stone keep") == 8
                    && level.getBlockState(chestPos).is(Blocks.CHEST), "fill keep replaced the chest or missed air");
            check(h, run(op, "mcheli fill" + area + "minecraft:dirt override") == 9
                    && level.getBlockState(from).is(Blocks.DIRT) && level.getBlockState(chestPos).is(Blocks.DIRT),
                    "fill override did not replace the blocks");
            check(h, level.getEntitiesOfClass(ItemEntity.class, new AABB(chestPos).inflate(3)).isEmpty(),
                    "fill dropped the chest's items");
            run(op, "mcheli fill" + area + "minecraft:air");
            check(h, refused(op, "mcheli fill" + area + "minecraft:stone override"), "fill override changed air");
            level.setBlockAndUpdate(chestPos, Blocks.STONE.defaultBlockState());
            check(h, run(op, "mcheli fill" + area + "minecraft:glass destroy") == 9
                    && level.getBlockState(chestPos).is(Blocks.GLASS)
                    && level.getEntitiesOfClass(ItemEntity.class, new AABB(chestPos).inflate(3)).isEmpty(),
                    "fill destroy did not replace the blocks without drops");
            check(h, refused(op, "mcheli fill 0 0 0 200 200 200 minecraft:stone"), "fill took more than 3,000,000 blocks");
            run(op, "mcheli fill" + area + "minecraft:air");
            check(h, run(op, "mcheli status tile") >= 0, "status tile did not run");
        } catch (CommandSyntaxException e) {
            throw h.assertionException(net.minecraft.network.chat.Component.literal(e.getMessage()));
        }
        h.succeed();
    }

    /**
     * {@code sendss} and {@code modlist} skip players without the mod; a request takes its answer
     * only from the player it asked, a screenshot only in order and as a PNG, which is saved to
     * {@code screenshots_op}; a refusal or a mod list ends the request.
     */
    private static void clientRequests(GameTestHelper h) {
        ServerPlayer asker = h.makeMockServerPlayerInLevel();
        ServerPlayer target = h.makeMockServerPlayerInLevel();
        CommandSourceStack op = asker.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER)
                .withSuppressedOutput();
        try {
            check(h, run(op, "mcheli sendss @s") == 0 && run(op, "mcheli modlist @a") == 0,
                    "sendss or modlist asked a player without the mod");
        } catch (CommandSyntaxException e) {
            throw h.assertionException(net.minecraft.network.chat.Component.literal(e.getMessage()));
        }

        int refused = ClientRequests.request(asker, target, Action.SCREENSHOT);
        check(h, ClientRequests.acceptScreenshot(asker, ScreenshotChunkPayload.refused(refused)) == Result.WRONG_PLAYER,
                "a request took an answer from a player it did not ask");
        check(h, ClientRequests.acceptModList(target, new ModListPayload(refused, true, List.of())) == Result.UNKNOWN_REQUEST,
                "a screenshot request took a mod list");
        check(h, ClientRequests.acceptScreenshot(target, ScreenshotChunkPayload.refused(refused)) == Result.REFUSED
                && ClientRequests.acceptScreenshot(target, ScreenshotChunkPayload.refused(refused))
                        == Result.UNKNOWN_REQUEST, "a refusal did not end the request");

        byte[] png = new byte[45_000];
        System.arraycopy(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 0, png, 0, 8);
        int outOfOrder = ClientRequests.request(asker, target, Action.SCREENSHOT);
        check(h, ClientRequests.acceptScreenshot(target, chunk(outOfOrder, png, 30_000)) == Result.BROKEN,
                "a screenshot took its pieces out of order");
        int notPng = ClientRequests.request(asker, target, Action.SCREENSHOT);
        check(h, ClientRequests.acceptScreenshot(target, new ScreenshotChunkPayload(notPng, 0, 3, new byte[] {1, 2, 3}))
                == Result.BROKEN, "a screenshot that is not a PNG was saved");

        Path folder = h.getLevel().getServer().getServerDirectory().resolve(ClientRequests.FOLDER);
        List<Path> before = pngs(folder);
        int saved = ClientRequests.request(asker, target, Action.SCREENSHOT);
        check(h, ClientRequests.acceptScreenshot(target, chunk(saved, png, 0)) == Result.INCOMPLETE
                && ClientRequests.acceptScreenshot(target, chunk(saved, png, 30_000)) == Result.SAVED,
                "a screenshot sent in order was not saved");
        List<Path> written = pngs(folder).stream().filter(file -> !before.contains(file)).toList();
        try {
            check(h, written.size() == 1 && Arrays.equals(Files.readAllBytes(written.getFirst()), png)
                    && written.getFirst().getFileName().toString().startsWith(target.getGameProfile().name() + "_"),
                    "the saved screenshot is not the one sent");
            for (Path file : written) {
                Files.delete(file);
            }
        } catch (IOException e) {
            throw h.assertionException(net.minecraft.network.chat.Component.literal(e.toString()));
        }

        int shared = ClientRequests.request(asker, target, Action.MOD_LIST);
        check(h, ClientRequests.acceptModList(target, new ModListPayload(shared, true, List.of("[minecraft]")))
                == Result.RELAYED, "the mod list was not relayed");
        int hidden = ClientRequests.request(null, target, Action.MOD_LIST);
        check(h, ClientRequests.acceptModList(target, new ModListPayload(hidden, false, List.of())) == Result.NOT_SHARED
                && ClientRequests.acceptModList(target, new ModListPayload(hidden, false, List.of()))
                        == Result.UNKNOWN_REQUEST, "an unshared mod list did not end the request");
        h.succeed();
    }

    private static ScreenshotChunkPayload chunk(int requestId, byte[] png, int offset) {
        int end = Math.min(png.length, offset + ScreenshotChunkPayload.MAX_CHUNK);
        return new ScreenshotChunkPayload(requestId, offset, png.length, Arrays.copyOfRange(png, offset, end));
    }

    private static List<Path> pngs(Path folder) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.toString().endsWith(".png")).toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
