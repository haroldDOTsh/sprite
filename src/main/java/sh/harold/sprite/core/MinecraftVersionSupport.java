package sh.harold.sprite.core;

import java.util.regex.Pattern;

public final class MinecraftVersionSupport {
    public static final String MINIMUM_VERSION = "1.21.9";
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?.*");

    private MinecraftVersionSupport() {
    }

    public static boolean isSupported(String minecraftVersion) {
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            return false;
        }

        var matcher = VERSION_PATTERN.matcher(minecraftVersion.trim());
        if (!matcher.matches()) {
            return false;
        }

        int major = parsePart(matcher.group(1));
        int minor = parsePart(matcher.group(2));
        int patch = parsePart(matcher.group(3));

        if (major == 1) {
            return minor > 21 || (minor == 21 && patch >= 9);
        }
        return major >= 26;
    }

    private static int parsePart(String value) {
        return value == null || value.isBlank() ? 0 : Integer.parseInt(value);
    }
}
