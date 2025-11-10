package sh.harold.sprite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.logging.Level;

public final class SpriteConfigLoader {
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final JavaPlugin plugin;

    public SpriteConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public SpriteConfig load() {
        try {
            Path dataFolder = plugin.getDataFolder().toPath();
            Files.createDirectories(dataFolder);

            Path configPath = dataFolder.resolve("config.yml");
            if (Files.notExists(configPath)) {
                plugin.saveResource("config.yml", false);
            }

            SpriteConfig config = readConfig(configPath);
            if (config.configVersion() != SpriteConfig.CURRENT_VERSION) {
                backupAndRegenerate(configPath);
                config = readConfig(configPath);
            }
            return config;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load sprite config; falling back to defaults.", ex);
            return new SpriteConfig(SpriteConfig.CURRENT_VERSION, AtlasPopulationMode.AUTOMATIC);
        }
    }

    private SpriteConfig readConfig(Path configPath) throws IOException {
        var yaml = YamlConfiguration.loadConfiguration(configPath.toFile());
        int version = yaml.getInt("config-version", SpriteConfig.CURRENT_VERSION);
        var modeName = yaml.getString("population.mode", AtlasPopulationMode.AUTOMATIC.name());
        AtlasPopulationMode mode;
        try {
            mode = AtlasPopulationMode.valueOf(modeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING,
                "Unknown atlas population mode '" + modeName + "', defaulting to AUTOMATIC.", ex);
            mode = AtlasPopulationMode.AUTOMATIC;
        }
        return new SpriteConfig(version, mode);
    }

    private void backupAndRegenerate(Path configPath) throws IOException {
        var backupName = "config-" + BACKUP_FORMAT.format(LocalDateTime.now()) + ".yml.bak";
        Path backupPath = configPath.resolveSibling(backupName);
        Files.move(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        plugin.getLogger().warning("Outdated config detected. Backed up to " + backupPath.getFileName()
            + " and regenerating defaults.");
        plugin.saveResource("config.yml", false);
    }
}
