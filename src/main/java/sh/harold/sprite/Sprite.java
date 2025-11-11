package sh.harold.sprite;

import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.sprite.atlas.AtlasCacheService;
import sh.harold.sprite.atlas.SpriteAtlasCatalog;
import sh.harold.sprite.atlas.SpriteAtlasService;
import sh.harold.sprite.command.SpriteCommandRegistrar;
import sh.harold.sprite.command.handler.RefreshAtlasCacheHandler;
import sh.harold.sprite.command.handler.SpriteViewCommandHandler;
import sh.harold.sprite.config.SpriteConfig;
import sh.harold.sprite.config.SpriteConfigLoader;

public final class Sprite extends JavaPlugin {
    private SpriteAtlasService atlasService;
    private SpriteCommandRegistrar commandRegistrar;
    private SpriteConfig spriteConfig;

    @Override
    public void onEnable() {
        spriteConfig = new SpriteConfigLoader(this).load();
        var cacheService = new AtlasCacheService(getDataFolder().toPath(), getLogger());
        var catalog = new SpriteAtlasCatalog(cacheService.getAtlasCacheDir(), getLogger());

        var serverVersion = getServer().getMinecraftVersion();
        atlasService = new SpriteAtlasService(this, cacheService, catalog, serverVersion, spriteConfig);
        atlasService.bootstrapFromCache();

        var refreshHandler = new RefreshAtlasCacheHandler(atlasService);
        var viewHandler = new SpriteViewCommandHandler(catalog, spriteConfig.titleDisplayDuration());

        commandRegistrar = new SpriteCommandRegistrar(this, refreshHandler, viewHandler);
        commandRegistrar.register();

        atlasService.refresh(null);
    }

    @Override
    public void onDisable() {
        // No-op for now.
    }
}
