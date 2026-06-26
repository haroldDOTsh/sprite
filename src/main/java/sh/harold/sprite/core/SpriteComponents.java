package sh.harold.sprite.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.Objects;

public final class SpriteComponents {
    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final GsonComponentSerializer JSON = GsonComponentSerializer.gson();

    private SpriteComponents() {
    }

    public static Component sprite(String atlasId, String spriteKey) {
        return Component.object(ObjectContents.sprite(atlasKey(atlasId), spriteKey(atlasId, spriteKey)));
    }

    public static String jsonPayload(String atlasId, String spriteKey) {
        return JSON.serialize(sprite(atlasId, spriteKey));
    }

    static Key atlasKey(String atlasId) {
        return key(atlasId, MINECRAFT_NAMESPACE);
    }

    static Key spriteKey(String atlasId, String spriteKey) {
        String namespace = namespace(atlasId);
        return key(spriteKey, namespace);
    }

    private static Key key(String value, String defaultNamespace) {
        String normalized = Objects.requireNonNull(value, "value").trim();
        if (normalized.contains(":")) {
            return Key.key(normalized);
        }
        return Key.key(defaultNamespace, normalized);
    }

    private static String namespace(String key) {
        if (key == null) {
            return MINECRAFT_NAMESPACE;
        }
        int separator = key.indexOf(':');
        if (separator <= 0) {
            return MINECRAFT_NAMESPACE;
        }
        return key.substring(0, separator);
    }
}
