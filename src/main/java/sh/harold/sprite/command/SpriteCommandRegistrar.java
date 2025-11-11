package sh.harold.sprite.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.Plugin;
import sh.harold.sprite.command.handler.RefreshAtlasCacheHandler;
import sh.harold.sprite.command.handler.SpriteViewCommandHandler;
import sh.harold.sprite.atlas.SpriteAtlasCatalog;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

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
            .then(buildReloadLiteral())
            .then(buildViewLiteral())
            .then(buildPreviewLiteral());
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildReloadLiteral() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("reload")
            .executes(refreshHandler::handleRefresh)
            .then(Commands.literal("all").executes(refreshHandler::handleRefresh))
            .then(Commands.literal("atlascache").executes(refreshHandler::handleRefresh));
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
                .suggests(this::suggestAtlasCategories)
                .executes(ctx -> viewHandler.handleAtlasCategory(ctx,
                    StringArgumentType.getString(ctx, "atlasCategory"), 1))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("page")
                    .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("atlasPage", IntegerArgumentType.integer(1))
                        .executes(ctx -> viewHandler.handleAtlasCategory(ctx,
                            StringArgumentType.getString(ctx, "atlasCategory"),
                            IntegerArgumentType.getInteger(ctx, "atlasPage"))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildPreviewLiteral() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("preview")
            .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("atlasCategory", StringArgumentType.string())
                .suggests(this::suggestAtlasCategories)
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("spriteId", StringArgumentType.greedyString())
                    .executes(ctx -> viewHandler.handlePreview(ctx,
                        StringArgumentType.getString(ctx, "atlasCategory"),
                        StringArgumentType.getString(ctx, "spriteId")))));
    }

    private CompletableFuture<Suggestions> suggestAtlasCategories(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return viewHandler.catalog().currentSnapshot()
            .map(snapshot -> {
                String remaining = builder.getRemaining();
                for (SpriteAtlasCatalog.AtlasEntry atlas : snapshot.atlases()) {
                    String namespaced = atlas.atlasId();
                    if (startsWithIgnoreCase(namespaced, remaining)) {
                        builder.suggest(namespaced);
                    }
                    if (atlas.isMinecraft()) {
                        String simple = atlas.simpleName();
                        if (startsWithIgnoreCase(simple, remaining)) {
                            builder.suggest(simple);
                        }
                    }
                }
                return builder.buildFuture();
            })
            .orElseGet(builder::buildFuture);
    }

    private static boolean startsWithIgnoreCase(String candidate, String partial) {
        if (partial == null || partial.isBlank()) {
            return true;
        }
        return candidate.toLowerCase(Locale.ROOT).startsWith(partial.toLowerCase(Locale.ROOT));
    }

    private static String atlasArgument(SpriteAtlasCatalog.AtlasEntry atlas) {
        return atlas.atlasId();
    }

}
