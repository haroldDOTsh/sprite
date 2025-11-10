package sh.harold.sprite.command.handler;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public final class SpriteViewCommandHandler {
    public int handleRootView(CommandContext<CommandSourceStack> context) {
        // Placeholder view root logic.
        return Command.SINGLE_SUCCESS;
    }

    public int handleAtlasCategory(CommandContext<CommandSourceStack> context) {
        // Placeholder category routing logic.
        return Command.SINGLE_SUCCESS;
    }

    public int handleSpecificCategory(CommandContext<CommandSourceStack> context) {
        // Placeholder specific view logic (pagination to follow).
        return Command.SINGLE_SUCCESS;
    }
}
