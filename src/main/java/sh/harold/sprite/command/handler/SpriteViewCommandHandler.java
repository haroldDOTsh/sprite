package sh.harold.sprite.command.handler;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import sh.harold.sprite.atlas.SpriteAtlasCatalog;
import sh.harold.sprite.core.Pagination;

import java.util.Optional;

public record SpriteViewCommandHandler(SpriteAtlasCatalog catalog) {
    private static final int PAGE_SIZE = 8;
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    public int handleRootView(CommandContext<CommandSourceStack> context, int page) {
        Optional<SpriteAtlasCatalog.CatalogSnapshot> snapshot = catalog.currentSnapshot();
        if (snapshot.isEmpty()) {
            sendNotReady(context);
            return Command.SINGLE_SUCCESS;
        }

        Pagination.Page<SpriteAtlasCatalog.AtlasEntry> slice = Pagination.slice(snapshot.get().atlases(), page, PAGE_SIZE);
        sendHeader(context, "Sprite Atlases", slice,
            slice.hasPrevious() ? "/sprite page " + (slice.page() - 1) : null,
            slice.hasNext() ? "/sprite page " + (slice.page() + 1) : null);

        if (slice.items().isEmpty()) {
            sendLine(context, Component.text("No atlases found.", NamedTextColor.GRAY));
            return Command.SINGLE_SUCCESS;
        }

        for (SpriteAtlasCatalog.AtlasEntry atlas : slice.items()) {
            Component line = Component.text(atlas.displayName(), NamedTextColor.YELLOW, TextDecoration.BOLD)
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(button("[CLICK TO VIEW]", NamedTextColor.GREEN,
                    "/sprite view " + quote(atlas.atlasId()),
                    "Browse " + atlas.groups().size() + " sprite groups"))
                .append(Component.text(" (" + atlas.groups().size() + " groups, " + atlas.spriteCount() + " sprites)",
                    NamedTextColor.GRAY));
            sendLine(context, line);
        }

        return Command.SINGLE_SUCCESS;
    }

    public int handleAtlasCategory(CommandContext<CommandSourceStack> context, String atlasId, int page) {
        Optional<SpriteAtlasCatalog.CatalogSnapshot> snapshot = catalog.currentSnapshot();
        if (snapshot.isEmpty()) {
            sendNotReady(context);
            return Command.SINGLE_SUCCESS;
        }

        SpriteAtlasCatalog.AtlasEntry atlas = snapshot.get().atlas(atlasId);
        if (atlas == null) {
            sendLine(context, Component.text("Unknown atlas: " + atlasId, NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        Pagination.Page<SpriteAtlasCatalog.SpriteGroup> slice = Pagination.slice(atlas.groups(), page, PAGE_SIZE);
        sendHeader(context, "Atlas " + atlas.displayName(), slice,
            slice.hasPrevious() ? "/sprite view " + quote(atlasId) + " page " + (slice.page() - 1) : null,
            slice.hasNext() ? "/sprite view " + quote(atlasId) + " page " + (slice.page() + 1) : null);

        if (slice.items().isEmpty()) {
            sendLine(context, Component.text("This atlas has no sprite groups.", NamedTextColor.GRAY));
            return Command.SINGLE_SUCCESS;
        }

        for (SpriteAtlasCatalog.SpriteGroup group : slice.items()) {
            Component line = Component.text(group.id(), NamedTextColor.AQUA)
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(button("[OPEN]", NamedTextColor.GREEN,
                    "/sprite view " + quote(atlasId) + " " + quote(group.id()),
                    "View " + group.size() + " sprite variants"));
            sendLine(context, line);
        }

        return Command.SINGLE_SUCCESS;
    }

    public int handleSpecificCategory(CommandContext<CommandSourceStack> context, String atlasId, String groupId, int page) {
        Optional<SpriteAtlasCatalog.CatalogSnapshot> snapshot = catalog.currentSnapshot();
        if (snapshot.isEmpty()) {
            sendNotReady(context);
            return Command.SINGLE_SUCCESS;
        }

        SpriteAtlasCatalog.AtlasEntry atlas = snapshot.get().atlas(atlasId);
        if (atlas == null) {
            sendLine(context, Component.text("Unknown atlas: " + atlasId, NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        SpriteAtlasCatalog.SpriteGroup group = atlas.group(groupId);
        if (group == null) {
            sendLine(context, Component.text("Unknown sprite group: " + groupId, NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        Pagination.Page<String> slice = Pagination.slice(group.sprites(), page, PAGE_SIZE);
        sendHeader(context, "Group " + group.id(), slice,
            slice.hasPrevious() ? "/sprite view " + quote(atlasId) + " " + quote(groupId) + " page " + (slice.page() - 1) : null,
            slice.hasNext() ? "/sprite view " + quote(atlasId) + " " + quote(groupId) + " page " + (slice.page() + 1) : null);

        if (slice.items().isEmpty()) {
            sendLine(context, Component.text("No sprite variants found.", NamedTextColor.GRAY));
            return Command.SINGLE_SUCCESS;
        }

        for (String sprite : slice.items()) {
            sendLine(context, buildSpriteLine(atlas.atlasId(), sprite));
        }

        return Command.SINGLE_SUCCESS;
    }

    private Component buildSpriteLine(String atlasId, String spriteKey) {
        String spriteDescriptor = atlasId + ":" + spriteKey;
        Component preview = MINI.deserialize("<white>[<sprite:" + spriteDescriptor + ">]</white>");
        Component atlasButton = button("[ATLAS]", NamedTextColor.AQUA, "/sprite view " + quote(atlasId), "Back to atlas view");
        Component idButton = copyButton("[ID]", NamedTextColor.GOLD, spriteDescriptor, "Copy sprite descriptor");
        Component miniMessageButton = copyButton("[MM]", NamedTextColor.YELLOW, "<sprite:" + spriteDescriptor + ">",
            "Copy MiniMessage tag");
        String tellrawPayload = "/tellraw @s {\"type\":\"minecraft:sprite\",\"sprite\":\"" + spriteDescriptor + "\"}";
        Component tellrawButton = copyButton("[TELLRAW]", NamedTextColor.LIGHT_PURPLE, tellrawPayload, "Copy tellraw command");

        return Component.text(spriteKey, NamedTextColor.YELLOW)
            .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
            .append(preview)
            .append(Component.text(" "))
            .append(atlasButton)
            .append(Component.text(" "))
            .append(idButton)
            .append(Component.text(" "))
            .append(miniMessageButton)
            .append(Component.text(" "))
            .append(tellrawButton);
    }

    private void sendHeader(CommandContext<CommandSourceStack> context, String title, Pagination.Page<?> slice, String prevCommand, String nextCommand) {
        Component header = navButton("<<", prevCommand, "Previous page")
            .append(Component.text(" "))
            .append(Component.text(title + " (Page " + slice.page() + " of " + slice.totalPages() + ")",
                NamedTextColor.GREEN))
            .append(Component.text(" "))
            .append(navButton(">>", nextCommand, "Next page"));
        sendLine(context, header);
    }

    private Component navButton(String label, String command, String hover) {
        Component base = Component.text(label, NamedTextColor.GOLD, TextDecoration.BOLD);
        if (command == null) {
            return base.color(NamedTextColor.DARK_GRAY);
        }
        return base.clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(Component.text(hover, NamedTextColor.GRAY));
    }

    private Component button(String label, NamedTextColor color, String command, String hover) {
        return Component.text(label, color)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(Component.text(hover, NamedTextColor.GRAY));
    }

    private Component copyButton(String label, NamedTextColor color, String payload, String hover) {
        return Component.text(label, color)
            .clickEvent(ClickEvent.copyToClipboard(payload))
            .hoverEvent(Component.text(hover, NamedTextColor.GRAY));
    }

    private void sendLine(CommandContext<CommandSourceStack> context, Component component) {
        context.getSource().getSender().sendMessage(component);
    }

    private void sendNotReady(CommandContext<CommandSourceStack> context) {
        sendLine(context, Component.text("Sprite atlas catalog is not ready yet. Please refresh first.", NamedTextColor.RED));
    }

    private String quote(String value) {
        if (value.indexOf(' ') >= 0) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        return value;
    }
}
