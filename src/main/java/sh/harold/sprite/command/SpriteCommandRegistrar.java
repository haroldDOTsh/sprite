package sh.harold.sprite.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.BrigadierCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.Plugin;
import sh.harold.sprite.command.handler.RefreshAtlasCacheHandler;
import sh.harold.sprite.command.handler.SpriteViewCommandHandler;

public final class SpriteCommandRegistrar {
    private final Plugin plugin;
    private final RefreshAtlasCacheHandler refreshHandler;
    private final SpriteViewCommandHandler viewHandler;

    public SpriteCommandRegistrar(
        Plugin plugin,
        RefreshAtlasCacheHandler refreshHandler,
        SpriteViewCommandHandler viewHandler
    ) {
        this.plugin = plugin;
        this.refreshHandler = refreshHandler;
        this.viewHandler = viewHandler;
    }

    public void register() {
        BrigadierCommand command = new BrigadierCommand("sprite");
        command.setPermission("sprite.command");
        command.register((dispatcher, rootNode) -> {
            rootNode.then(LiteralArgumentBuilder.<CommandSourceStack>literal("refreshatlascache")
                .executes(refreshHandler::handleRefresh));

            rootNode.then(buildViewLiteral());
        });

        CommandMap commandMap = plugin.getServer().getCommandMap();
        commandMap.register(plugin.getName().toLowerCase(), command);
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildViewLiteral() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("view")
            .executes(viewHandler::handleRootView)
            .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("atlasCategory", StringArgumentType.word())
                .executes(viewHandler::handleAtlasCategory)
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("uiCategory", StringArgumentType.word())
                    .executes(viewHandler::handleSpecificCategory)));
    }
}
