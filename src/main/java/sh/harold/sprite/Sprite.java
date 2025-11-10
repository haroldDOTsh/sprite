package sh.harold.sprite;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.sprite.atlas.AtlasCacheService;
import sh.harold.sprite.command.SpriteCommandRegistrar;
import sh.harold.sprite.command.handler.RefreshAtlasCacheHandler;
import sh.harold.sprite.command.handler.SpriteViewCommandHandler;

public final class Sprite extends JavaPlugin {
    private AtlasCacheService atlasCacheService;
    private SpriteCommandRegistrar commandRegistrar;

    @Override
    public void onEnable() {
        atlasCacheService = new AtlasCacheService(getDataFolder().toPath(), getLogger());
        RefreshAtlasCacheHandler refreshHandler = new RefreshAtlasCacheHandler(atlasCacheService);
        SpriteViewCommandHandler viewHandler = new SpriteViewCommandHandler();
        commandRegistrar = new SpriteCommandRegistrar(this, refreshHandler, viewHandler);
        commandRegistrar.register();

        String serverVersion = getServer().getMinecraftVersion();

        Bukkit.getScheduler().runTaskAsynchronously(this, () ->
            atlasCacheService.refreshAtlases(serverVersion)
        );
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
