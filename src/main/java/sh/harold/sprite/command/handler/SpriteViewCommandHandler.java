package sh.harold.sprite.command.handler;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import sh.harold.sprite.atlas.SpriteAtlasCatalog;
import sh.harold.sprite.core.Pagination;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record SpriteViewCommandHandler(SpriteAtlasCatalog catalog) {
    private static final int ROOT_PAGE_SIZE = 6;
    private static final int MENU_PAGE_SIZE = 16;
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final int CHAT_WIDTH_CHARACTERS = 53; // 320px chat width / 6px glyph width
    private static final Component PAGE_RULE = MINI.deserialize(
        "<blue><strikethrough>" + "-".repeat(CHAT_WIDTH_CHARACTERS) + "</strikethrough></blue>");
    private static final Component NAVBAR_SPACER = Component.text(" ");
    private static final String HEADER_BADGE_MENU = "MENU";
    private static final String HEADER_BADGE_ATLAS = "ATLAS";
    private static final String HEADER_BADGE_GROUP = "GROUP";
    private static final NamedTextColor BREADCRUMB_COLOR = NamedTextColor.GOLD;
    private static final Component BREADCRUMB_TOOLTIP = MINI.deserialize("<yellow><bold>CLICK </bold></yellow><gray>to return to previous menu!</gray>");

    public int handleRootView(CommandContext<CommandSourceStack> context, int page) {
        Optional<SpriteAtlasCatalog.CatalogSnapshot> snapshot = catalog.currentSnapshot();
        if (snapshot.isEmpty()) {
            sendNotReady(context);
            return Command.SINGLE_SUCCESS;
        }

        Pagination.Page<SpriteAtlasCatalog.AtlasEntry> slice = Pagination.slice(snapshot.get().atlases(), page, ROOT_PAGE_SIZE);
        sendPageRule(context);
        sendHeader(context, "Sprite Atlases", HEADER_BADGE_MENU, slice, false, null,
            slice.hasPrevious() ? command("sprite", "page", Integer.toString(slice.page() - 1)) : null,
            slice.hasNext() ? command("sprite", "page", Integer.toString(slice.page() + 1)) : null);
        sendNavbarSpacer(context);

        if (slice.items().isEmpty()) {
            sendLine(context, Component.text("No atlases found.", NamedTextColor.GRAY));
            sendPageRule(context);
            return Command.SINGLE_SUCCESS;
        }

        for (SpriteAtlasCatalog.AtlasEntry atlas : slice.items()) {
            Component line = Component.text(atlas.displayName(), NamedTextColor.YELLOW)
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(button("[CLICK TO VIEW]", NamedTextColor.GREEN,
                    command("sprite", "view", atlasCommandArgument(atlas)),
                    "Browse " + atlas.groups().size() + " sprite groups"))
                .append(Component.text(" (" + atlas.groups().size() + " groups, " + atlas.spriteCount() + " sprites)",
                    NamedTextColor.GRAY));
            sendLine(context, line);
        }

        sendPageRule(context);
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
        String atlasCommand = atlasCommandArgument(atlas);

        Pagination.Page<SpriteAtlasCatalog.SpriteGroup> slice = Pagination.slice(atlas.groups(), page, MENU_PAGE_SIZE);
        sendPageRule(context);
        sendHeader(context, atlas.displayName(), HEADER_BADGE_ATLAS, slice, true, command("sprite"),
            slice.hasPrevious() ? command("sprite", "view", atlasCommand, "page", Integer.toString(slice.page() - 1)) : null,
            slice.hasNext() ? command("sprite", "view", atlasCommand, "page", Integer.toString(slice.page() + 1)) : null);
        sendNavbarSpacer(context);

        if (slice.items().isEmpty()) {
            sendLine(context, Component.text("This atlas has no sprite groups.", NamedTextColor.GRAY));
            sendPageRule(context);
            return Command.SINGLE_SUCCESS;
        }

        for (SpriteAtlasCatalog.SpriteGroup group : slice.items()) {
            Component line = Component.text(group.id(), NamedTextColor.AQUA)
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(button("[OPEN]", NamedTextColor.GREEN,
                    command("sprite", "view", atlasCommand, group.id()),
                    "View " + group.size() + " sprite variants"));
            sendLine(context, line);
        }

        sendPageRule(context);
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
        String atlasCommand = atlasCommandArgument(atlas);
        SpriteAtlasCatalog.SpriteGroup group = atlas.group(groupId);
        if (group == null) {
            sendLine(context, Component.text("Unknown sprite group: " + groupId, NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        Pagination.Page<String> slice = Pagination.slice(group.sprites(), page, MENU_PAGE_SIZE);
        sendPageRule(context);
        sendHeader(context, group.id(), HEADER_BADGE_GROUP, slice, true, command("sprite", "view", atlasCommand),
            slice.hasPrevious() ? command("sprite", "view", atlasCommand, groupId, "page", Integer.toString(slice.page() - 1)) : null,
            slice.hasNext() ? command("sprite", "view", atlasCommand, groupId, "page", Integer.toString(slice.page() + 1)) : null);
        sendNavbarSpacer(context);

        if (slice.items().isEmpty()) {
            sendLine(context, Component.text("No sprite variants found.", NamedTextColor.GRAY));
            sendPageRule(context);
            return Command.SINGLE_SUCCESS;
        }

        for (String sprite : slice.items()) {
            sendLine(context, buildSpriteLine(atlas.atlasId(), sprite));
        }

        sendPageRule(context);
        return Command.SINGLE_SUCCESS;
    }

    private Component buildSpriteLine(String atlasId, String spriteKey) {
        String spriteDescriptor = atlasId + ":" + spriteKey;
        String miniMessageTag = buildMiniMessageSpriteTag(atlasId, spriteKey);
        Component name = buildSpriteName(spriteKey);
        Component icon = buildSpriteIcon(miniMessageTag);
        Component miniMessageButton = copyButton("[MM]", NamedTextColor.YELLOW, "<" + miniMessageTag + ">",
            "Copy MiniMessage tag");
        String jsonPayload = "{\"type\":\"minecraft:sprite\",\"sprite\":\"" + spriteDescriptor + "\"}";
        Component jsonButton = copyButton("[JSON]", NamedTextColor.AQUA, jsonPayload, "Copy JSON payload");

        return name
            .append(Component.text(" "))
            .append(icon)
            .append(Component.text(" "))
            .append(Component.text("-", NamedTextColor.GRAY))
            .append(Component.text(" "))
            .append(miniMessageButton)
            .append(Component.text(" "))
            .append(jsonButton);
    }

    private Component buildSpriteName(String spriteKey) {
        String truncated = truncateSpriteKey(spriteKey);
        return Component.text(truncated, NamedTextColor.YELLOW)
            .clickEvent(ClickEvent.copyToClipboard(spriteKey))
            .hoverEvent(Component.text("Copy full path: " + spriteKey, NamedTextColor.GRAY));
    }

    private Component buildSpriteIcon(String miniMessageTag) {
        Component icon = MINI.deserialize("<reset><" + miniMessageTag + ">");
        return Component.text("[ ", NamedTextColor.GRAY)
            .append(icon)
            .append(Component.text(" ]", NamedTextColor.GRAY));
    }

    private String truncateSpriteKey(String spriteKey) {
        int lastSlash = spriteKey.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == spriteKey.length() - 1) {
            return spriteKey;
        }
        return spriteKey.substring(lastSlash + 1);
    }

    private void sendHeader(CommandContext<CommandSourceStack> context, String title, String badge,
                            Pagination.Page<?> slice, boolean showPageIndicator, String breadcrumbCommand,
                            String prevCommand, String nextCommand) {
        Component header = navButton("<<", prevCommand, "Previous page")
            .append(Component.text(" "))
            .append(buildHeaderLabel(title, badge, breadcrumbCommand))
            .append(buildPageIndicator(slice, showPageIndicator))
            .append(Component.text(" "))
            .append(navButton(">>", nextCommand, "Next page"));
        sendLine(context, header);
    }

    private Component buildHeaderLabel(String title, String badge, String breadcrumbCommand) {
        List<Component> segments = buildBreadcrumbSegments(title);
        Component label = Component.empty();
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                label = label.append(applyBreadcrumbInteractivity(Component.text("/", NamedTextColor.GRAY), breadcrumbCommand));
            }
            label = label.append(applyBreadcrumbInteractivity(segments.get(i), breadcrumbCommand));
        }

        if (badge != null && !badge.isBlank()) {
            label = label.append(Component.text(" "))
                .append(Component.text("(" + badge + ")", NamedTextColor.DARK_GRAY));
        }
        return label;
    }

    private List<Component> buildBreadcrumbSegments(String title) {
        List<Component> segments = new ArrayList<>();
        if (title == null || title.isBlank()) {
            return segments;
        }
        String[] parts = title.split("/");
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            segments.add(breadcrumbSegment(part));
        }
        if (segments.isEmpty()) {
            segments.add(breadcrumbSegment(title));
        }
        return segments;
    }

    private Component breadcrumbSegment(String display) {
        return Component.text(display, BREADCRUMB_COLOR);
    }

    private Component applyBreadcrumbInteractivity(Component component, String commandToRun) {
        if (commandToRun == null || commandToRun.isBlank()) {
            return component;
        }
        return component.clickEvent(ClickEvent.runCommand(commandToRun))
            .hoverEvent(BREADCRUMB_TOOLTIP);
    }

    private Component buildPageIndicator(Pagination.Page<?> slice, boolean showPageIndicator) {
        if (!showPageIndicator || slice == null) {
            return Component.empty();
        }
        return Component.text(" (Page " + slice.page() + " of " + slice.totalPages() + ")", NamedTextColor.GOLD);
    }

    private void sendNavbarSpacer(CommandContext<CommandSourceStack> context) {
        sendLine(context, NAVBAR_SPACER);
    }

    private Component navButton(String label, String command, String hover) {
        Component base = Component.text(label, NamedTextColor.GOLD);
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

    private String buildMiniMessageSpriteTag(String atlasId, String spriteKey) {
        return "sprite:\"" + escapeMiniMessageArg(atlasId) + "\":" + escapeMiniMessageArg(spriteKey);
    }

    private String escapeMiniMessageArg(String value) {
        return value.replace("\"", "\\\"");
    }

    private void sendPageRule(CommandContext<CommandSourceStack> context) {
        sendLine(context, PAGE_RULE);
    }

    private void sendLine(CommandContext<CommandSourceStack> context, Component component) {
        context.getSource().getSender().sendMessage(component);
    }

    private void sendNotReady(CommandContext<CommandSourceStack> context) {
        sendLine(context, Component.text("Sprite atlas catalog is not ready yet. Please refresh first.", NamedTextColor.RED));
    }

    private String atlasCommandArgument(SpriteAtlasCatalog.AtlasEntry atlas) {
        return atlas.isMinecraft() ? atlas.simpleName() : atlas.atlasId();
    }

    private String command(String... parts) {
        StringBuilder builder = new StringBuilder("/");
        boolean first = true;
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String formatted = formatArgument(part);
            if (!first) {
                builder.append(' ');
            }
            builder.append(formatted);
            first = false;
        }
        return builder.toString();
    }

    private String formatArgument(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "\"\"";
        }
        if (needsQuoting(trimmed)) {
            return "\"" + trimmed.replace("\"", "\\\"") + "\"";
        }
        return trimmed;
    }

    private boolean needsQuoting(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isWhitespace(ch) || ch == '/') {
                return true;
            }
        }
        return false;
    }
}
