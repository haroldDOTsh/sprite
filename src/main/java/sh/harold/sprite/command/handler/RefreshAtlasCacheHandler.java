package sh.harold.sprite.command.handler;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import sh.harold.sprite.atlas.AtlasCacheService;

public final class RefreshAtlasCacheHandler {
    private final AtlasCacheService atlasCacheService;

    public RefreshAtlasCacheHandler(AtlasCacheService atlasCacheService) {
        this.atlasCacheService = atlasCacheService;
    }

    public int handleRefresh(CommandContext<CommandSourceStack> context) {
        // Placeholder logic; wiring will follow in subsequent iterations.
        return Command.SINGLE_SUCCESS;
    }

    public AtlasCacheService getAtlasCacheService() {
        return atlasCacheService;
    }
}
