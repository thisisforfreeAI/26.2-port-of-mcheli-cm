package io.github.mcheliceport.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.emitter.YamlDump;
import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The {@code /mcheli} command (upstream {@code MCH_Command}). Ported so far:
 * <ul>
 *   <li>{@code /mcheli dumpyaml [folder]}: writes all content that was not loaded from YAML (that
 *       is, TXT content) as YAML files into {@code <game dir>/<folder>}, by default
 *       {@code mcheli_yaml_dump}. Upstream accepted any path; the port only takes a folder name,
 *       so operators cannot write outside the game folder.</li>
 * </ul>
 * As upstream, it needs operator permission (level 2).
 */
@EventBusSubscriber(modid = McheliCePort.MOD_ID)
public final class McheliCommand {

    public static final String DEFAULT_DUMP_FOLDER = "mcheli_yaml_dump";
    private static final Pattern FOLDER_NAME = Pattern.compile("[A-Za-z0-9_.-]+");

    private McheliCommand() {
    }

    @SubscribeEvent
    static void registerCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mcheli")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("dumpyaml")
                        .executes(context -> dumpYaml(context, DEFAULT_DUMP_FOLDER))
                        .then(Commands.argument("folder", StringArgumentType.word())
                                .executes(context -> dumpYaml(context, StringArgumentType.getString(context, "folder"))))));
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
