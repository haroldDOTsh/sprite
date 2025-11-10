package sh.harold.sprite;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.sprite.atlas.AtlasCacheService;

public final class Sprite extends JavaPlugin {
    private AtlasCacheService atlasCacheService;

    @Override
    public void onEnable() {
        atlasCacheService = new AtlasCacheService(getDataFolder().toPath(), getLogger());
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
