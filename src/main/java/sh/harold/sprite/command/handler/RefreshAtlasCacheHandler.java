package sh.harold.sprite.command.handler;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import sh.harold.sprite.atlas.SpriteAtlasService;

public record RefreshAtlasCacheHandler(SpriteAtlasService atlasService) {
    public int handleRefresh(CommandContext<CommandSourceStack> context) {
        atlasService.refresh(context.getSource().getSender());
        return Command.SINGLE_SUCCESS;
    }
}
