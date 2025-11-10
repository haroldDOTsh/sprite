package sh.harold.sprite.config;

public record SpriteConfig(int configVersion, AtlasPopulationMode populationMode) {
    public static final int CURRENT_VERSION = 1;
}
