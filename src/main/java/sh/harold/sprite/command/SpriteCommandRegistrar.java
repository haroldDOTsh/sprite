package sh.harold.sprite.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.Plugin;
import sh.harold.sprite.command.handler.RefreshAtlasCacheHandler;
import sh.harold.sprite.command.handler.SpriteViewCommandHandler;

import java.util.List;

public record SpriteCommandRegistrar(
    Plugin plugin,
    RefreshAtlasCacheHandler refreshHandler,
    SpriteViewCommandHandler viewHandler
) {
    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            LiteralCommandNode<CommandSourceStack> root = buildRootNode().build();
            registrar.register(plugin.getPluginMeta(), root, "sprite", List.of());
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildRootNode() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("sprite")
            .requires(stack -> stack.getSender().hasPermission("sprite.command"))
            .executes(ctx -> viewHandler.handleRootView(ctx, 1))
            .then(buildRootPaginationLiteral())
            .then(buildRefreshLiteral())
            .then(buildViewLiteral());
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildRefreshLiteral() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("refreshatlascache")
            .executes(refreshHandler::handleRefresh);
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildRootPaginationLiteral() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("page")
            .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("rootPage", IntegerArgumentType.integer(1))
                .executes(ctx -> viewHandler.handleRootView(ctx, IntegerArgumentType.getInteger(ctx, "rootPage"))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildViewLiteral() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("view")
            .executes(ctx -> viewHandler.handleRootView(ctx, 1))
            .then(buildRootPaginationLiteral())
            .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("atlasCategory", StringArgumentType.string())
                .executes(ctx -> viewHandler.handleAtlasCategory(ctx,
                    StringArgumentType.getString(ctx, "atlasCategory"), 1))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("page")
                    .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("atlasPage", IntegerArgumentType.integer(1))
                        .executes(ctx -> viewHandler.handleAtlasCategory(ctx,
                            StringArgumentType.getString(ctx, "atlasCategory"),
                            IntegerArgumentType.getInteger(ctx, "atlasPage")))))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("uiCategory", StringArgumentType.string())
                    .executes(ctx -> viewHandler.handleSpecificCategory(ctx,
                        StringArgumentType.getString(ctx, "atlasCategory"),
                        StringArgumentType.getString(ctx, "uiCategory"), 1))
                    .then(LiteralArgumentBuilder.<CommandSourceStack>literal("page")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("groupPage", IntegerArgumentType.integer(1))
                            .executes(ctx -> viewHandler.handleSpecificCategory(ctx,
                                StringArgumentType.getString(ctx, "atlasCategory"),
                                StringArgumentType.getString(ctx, "uiCategory"),
                                IntegerArgumentType.getInteger(ctx, "groupPage")))))));
    }
}
