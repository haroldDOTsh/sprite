package sh.harold.sprite.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftVersionSupportTest {
    @Test
    void rejectsVersionsBeforeSpriteObjectSupport() {
        assertFalse(MinecraftVersionSupport.isSupported(null));
        assertFalse(MinecraftVersionSupport.isSupported(""));
        assertFalse(MinecraftVersionSupport.isSupported("1.20.6"));
        assertFalse(MinecraftVersionSupport.isSupported("1.21"));
        assertFalse(MinecraftVersionSupport.isSupported("1.21.8"));
    }

    @Test
    void acceptsSpriteObjectSupportFloorAndNewerReleaseLines() {
        assertTrue(MinecraftVersionSupport.isSupported("1.21.9"));
        assertTrue(MinecraftVersionSupport.isSupported("1.21.10"));
        assertTrue(MinecraftVersionSupport.isSupported("1.21.11"));
        assertTrue(MinecraftVersionSupport.isSupported("1.22"));
        assertTrue(MinecraftVersionSupport.isSupported("26.1"));
        assertTrue(MinecraftVersionSupport.isSupported("26.2"));
    }
}
