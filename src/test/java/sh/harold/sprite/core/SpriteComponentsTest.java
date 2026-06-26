package sh.harold.sprite.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.object.SpriteObjectContents;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SpriteComponentsTest {
    @Test
    void buildsSpriteObjectComponentWithAtlasAndSpriteKeys() {
        var component = assertInstanceOf(ObjectComponent.class,
            SpriteComponents.sprite("minecraft:blocks", "block/stone"));
        var contents = assertInstanceOf(SpriteObjectContents.class, component.contents());

        assertEquals(Key.key("minecraft:blocks"), contents.atlas());
        assertEquals(Key.key("minecraft", "block/stone"), contents.sprite());
    }

    @Test
    void derivesSpriteNamespaceFromNonMinecraftAtlasWhenSpriteIsUnqualified() {
        var component = assertInstanceOf(ObjectComponent.class,
            SpriteComponents.sprite("example:blocks", "block/widget"));
        var contents = assertInstanceOf(SpriteObjectContents.class, component.contents());

        assertEquals(Key.key("example:blocks"), contents.atlas());
        assertEquals(Key.key("example", "block/widget"), contents.sprite());
    }

    @Test
    void serializesJsonPayloadThroughAdventureSerializer() {
        var component = SpriteComponents.sprite("minecraft:blocks", "block/stone");
        String json = SpriteComponents.jsonPayload("minecraft:blocks", "block/stone");

        assertEquals(component, GsonComponentSerializer.gson().deserialize(json));
    }
}
