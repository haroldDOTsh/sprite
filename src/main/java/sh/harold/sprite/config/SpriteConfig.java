package sh.harold.sprite.config;

import java.time.Duration;
import java.util.Objects;

public record SpriteConfig(
    int configVersion,
    AtlasPopulationMode populationMode,
    Duration titleDisplayDuration
) {
    public static final int CURRENT_VERSION = 2;
    public static final Duration DEFAULT_TITLE_DISPLAY_DURATION = Duration.ofSeconds(2);

    public SpriteConfig {
        populationMode = Objects.requireNonNullElse(populationMode, AtlasPopulationMode.AUTOMATIC);
        Duration sanitized = titleDisplayDuration == null || titleDisplayDuration.isNegative()
            ? DEFAULT_TITLE_DISPLAY_DURATION
            : titleDisplayDuration;
        this.populationMode = populationMode;
        this.titleDisplayDuration = sanitized;
    }
}
