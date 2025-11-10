package sh.harold.sprite.atlas;

import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.sprite.config.SpriteConfig;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coordinates atlas downloads with the in-memory sprite catalog.
 */
public final class SpriteAtlasService {
    private final JavaPlugin plugin;
    private final AtlasCacheService cacheService;
    private final SpriteAtlasCatalog catalog;
    private final String serverVersion;
    private final SpriteConfig config;
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private final Logger logger;

    public SpriteAtlasService(
        JavaPlugin plugin,
        AtlasCacheService cacheService,
        SpriteAtlasCatalog catalog,
        String serverVersion,
        SpriteConfig config
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.cacheService = Objects.requireNonNull(cacheService, "cacheService");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.serverVersion = Objects.requireNonNull(serverVersion, "serverVersion");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = plugin.getLogger();
    }

    public SpriteAtlasCatalog catalog() {
        return catalog;
    }

    public void refresh(CommandSender initiator) {
        if (!refreshInProgress.compareAndSet(false, true)) {
            if (initiator != null) {
                initiator.sendMessage(Component.text("Atlas refresh already running.", NamedTextColor.YELLOW));
            }
            return;
        }

        if (initiator != null) {
            initiator.sendMessage(Component.text("Refreshing sprite atlases...", NamedTextColor.GRAY));
        }
        logger.info("Starting sprite atlas refresh.");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject assetIndex = cacheService.refreshAtlases(serverVersion, config);
                if (assetIndex == null) {
                    notifyFailure(initiator, "Unable to resolve asset index for " + serverVersion + ".");
                    return;
                }

                catalog.rebuild(assetIndex);
                SpriteAtlasCatalog.CatalogSnapshot snapshot = catalog.snapshotOrEmpty();
                notifySuccess(initiator, snapshot);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Failed to rebuild sprite atlas catalog", ex);
                notifyFailure(initiator, "Failed to rebuild atlas catalog. Check logs for details.");
            } finally {
                refreshInProgress.set(false);
            }
        });
    }

    public void bootstrapFromCache() {
        JsonObject cachedIndex = cacheService.readStoredAssetIndex();
        if (cachedIndex == null) {
            return;
        }
        try {
            catalog.rebuild(cachedIndex);
            logger.info("Loaded sprite catalog from cached asset index.");
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to rebuild catalog from cached asset index", ex);
        }
    }

    private void notifySuccess(CommandSender initiator, SpriteAtlasCatalog.CatalogSnapshot snapshot) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            logger.info("Sprite atlas refresh complete (" + snapshot.atlases().size() + " atlases, " + snapshot.totalSprites() + " sprites).");
            if (initiator != null) {
                initiator.sendMessage(Component.text(
                    "Sprite atlas refresh complete (" + snapshot.atlases().size() + " atlases, " + snapshot.totalSprites() + " sprites).",
                    NamedTextColor.GREEN));
            }
        });
    }

    private void notifyFailure(CommandSender initiator, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            logger.warning(message);
            if (initiator != null) {
                initiator.sendMessage(Component.text(message, NamedTextColor.RED));
            }
        });
    }
}
