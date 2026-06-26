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
import sh.harold.sprite.core.MinecraftVersionSupport;

public final class Sprite extends JavaPlugin {
    private SpriteAtlasService atlasService;
    private SpriteCommandRegistrar commandRegistrar;
    private SpriteConfig spriteConfig;

    @Override
    public void onEnable() {
        var serverVersion = getServer().getMinecraftVersion();
        if (!MinecraftVersionSupport.isSupported(serverVersion)) {
            getLogger().severe("sprite requires Minecraft " + MinecraftVersionSupport.MINIMUM_VERSION
                + " or newer; detected " + serverVersion + ". Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        spriteConfig = new SpriteConfigLoader(this).load();
        var cacheService = new AtlasCacheService(getDataFolder().toPath(), getLogger());
        var catalog = new SpriteAtlasCatalog(cacheService.getAtlasCacheDir(), getLogger());

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
