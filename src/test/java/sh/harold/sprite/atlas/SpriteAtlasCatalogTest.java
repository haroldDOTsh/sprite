package sh.harold.sprite.atlas;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.harold.sprite.config.AtlasPopulationMode;
import sh.harold.sprite.config.SpriteConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpriteAtlasCatalogTest {
    private static final Logger LOGGER = Logger.getLogger(SpriteAtlasCatalogTest.class.getName());

    @TempDir
    Path tempDir;

    @Test
    void expandsSupportedAtlasSourceTypes() throws IOException {
        write(tempDir.resolve("textures.index"), String.join("\n",
            "minecraft/textures/block/stone.png",
            "minecraft/textures/block/oak_log.png",
            "minecraft/textures/item/stick.png",
            "minecraft/textures/entity/sheep/white.png"));
        write(tempDir.resolve("minecraft/atlases/blocks.json"), """
            {"sources":[
              {"type":"minecraft:directory","source":"block","prefix":"block/"},
              {"type":"minecraft:single","resource":"minecraft:item/stick"},
              {"type":"minecraft:paletted_permutations","textures":["minecraft:entity/sheep/white"],"permutations":{"red":"minecraft:color/red","blue":"minecraft:color/blue"}}
            ]}
            """);

        var catalog = new SpriteAtlasCatalog(tempDir, LOGGER);
        catalog.rebuild(emptyAssetIndex());

        var atlas = catalog.snapshotOrEmpty().atlas("minecraft:blocks");
        assertNotNull(atlas);
        List<String> sprites = atlas.groups().stream()
            .flatMap(group -> group.sprites().stream())
            .toList();

        assertTrue(sprites.contains("block/stone"));
        assertTrue(sprites.contains("block/oak_log"));
        assertTrue(sprites.contains("item/stick"));
        assertTrue(sprites.contains("entity/sheep/white_red"));
        assertTrue(sprites.contains("entity/sheep/white_blue"));
    }

    @Test
    void automaticCachesAreIsolatedByMinecraftVersion() throws IOException {
        var service = new AtlasCacheService(tempDir, LOGGER);
        var config = new SpriteConfig(2, AtlasPopulationMode.AUTOMATIC, Duration.ofSeconds(2));
        Path firstVersion = service.getAtlasCacheDir("1.21.9", config);
        Path secondVersion = service.getAtlasCacheDir("1.21.10", config);

        writeDirectoryAtlas(firstVersion, "old_block");
        writeDirectoryAtlas(secondVersion, "new_block");

        var firstCatalog = new SpriteAtlasCatalog(firstVersion, LOGGER);
        var secondCatalog = new SpriteAtlasCatalog(secondVersion, LOGGER);
        firstCatalog.rebuild(emptyAssetIndex());
        secondCatalog.rebuild(emptyAssetIndex());

        List<String> firstSprites = sprites(firstCatalog);
        List<String> secondSprites = sprites(secondCatalog);

        assertNotEquals(firstVersion, secondVersion);
        assertTrue(firstSprites.contains("block/old_block"));
        assertFalse(firstSprites.contains("block/new_block"));
        assertTrue(secondSprites.contains("block/new_block"));
        assertFalse(secondSprites.contains("block/old_block"));
    }

    @Test
    void manualModeKeepsLegacyAtlasCacheRoot() {
        var service = new AtlasCacheService(tempDir, LOGGER);
        var config = new SpriteConfig(2, AtlasPopulationMode.MANUAL, Duration.ofSeconds(2));

        assertEquals(tempDir.resolve("atlas-cache"), service.getAtlasCacheDir("1.21.9", config));
    }

    private void writeDirectoryAtlas(Path cacheRoot, String blockName) throws IOException {
        write(cacheRoot.resolve("textures.index"), "minecraft/textures/block/" + blockName + ".png");
        write(cacheRoot.resolve("minecraft/atlases/blocks.json"), """
            {"sources":[{"type":"minecraft:directory","source":"block","prefix":"block/"}]}
            """);
    }

    private List<String> sprites(SpriteAtlasCatalog catalog) {
        var atlas = catalog.snapshotOrEmpty().atlas("minecraft:blocks");
        assertNotNull(atlas);
        return atlas.groups().stream()
            .flatMap(group -> group.sprites().stream())
            .toList();
    }

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private JsonObject emptyAssetIndex() {
        var root = new JsonObject();
        root.add("objects", new JsonObject());
        return root;
    }
}
