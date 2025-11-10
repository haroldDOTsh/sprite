package sh.harold.sprite.atlas;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fetches atlas definitions from Mojang's asset service and stores them locally
 * so later gameplay code can read from disk instead of hitting the network.
 */
public final class AtlasCacheService {
    private static final URI MANIFEST_URI = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final String ASSET_PREFIX = "minecraft/atlases/";
    private static final String ATLAS_SUFFIX = ".json";
    private static final String RESOURCE_BASE = "https://resources.download.minecraft.net/";

    private final HttpClient httpClient;
    private final Path atlasCacheDir;
    private final Logger logger;

    public AtlasCacheService(Path dataFolder, Logger logger) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
        this.atlasCacheDir = Objects.requireNonNull(dataFolder, "dataFolder").resolve("atlas-cache");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void refreshAtlases(String serverVersion) {
        try {
            Files.createDirectories(atlasCacheDir);
            JsonObject manifestJson = fetchJson(MANIFEST_URI);
            JsonObject versionEntry = findVersionEntry(manifestJson, serverVersion);
            if (versionEntry == null) {
                logger.warning("Unable to find version '" + serverVersion + "' in Mojang manifest; atlas caching skipped.");
                return;
            }

            URI versionUri = URI.create(versionEntry.get("url").getAsString());
            JsonObject versionJson = fetchJson(versionUri);
            JsonObject assetIndex = versionJson.getAsJsonObject("assetIndex");
            if (assetIndex == null || !assetIndex.has("url")) {
                logger.warning("Version metadata missing asset index information; atlas caching skipped.");
                return;
            }

            URI assetIndexUri = URI.create(assetIndex.get("url").getAsString());
            JsonObject assetIndexJson = fetchJson(assetIndexUri);
            JsonObject objects = assetIndexJson.getAsJsonObject("objects");
            if (objects == null) {
                logger.warning("Asset index contained no objects; atlas caching skipped.");
                return;
            }

            int processed = 0;
            for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
                String path = entry.getKey();
                if (!path.startsWith(ASSET_PREFIX) || !path.endsWith(ATLAS_SUFFIX)) {
                    continue;
                }

                JsonObject descriptor = entry.getValue().getAsJsonObject();
                if (!descriptor.has("hash")) {
                    continue;
                }

                String hash = descriptor.get("hash").getAsString();
                URI atlasUri = buildResourceUri(hash);
                Path destination = atlasCacheDir.resolve(path.substring(ASSET_PREFIX.length()));
                downloadAtlas(atlasUri, destination, hash);
                processed++;
            }

            logger.info("Atlas cache prepared with " + processed + " entries at " + atlasCacheDir.toAbsolutePath());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.log(Level.SEVERE, "Failed to cache Minecraft atlases", ex);
        }
    }

    private JsonObject findVersionEntry(JsonObject manifestJson, String serverVersion) {
        if (manifestJson == null || serverVersion == null) {
            return null;
        }

        JsonArray versions = manifestJson.getAsJsonArray("versions");
        if (versions == null) {
            return null;
        }

        for (JsonElement versionElement : versions) {
            JsonObject version = versionElement.getAsJsonObject();
            if (serverVersion.equals(version.get("id").getAsString())) {
                return version;
            }
        }
        return null;
    }

    private URI buildResourceUri(String hash) {
        String subFolder = hash.substring(0, 2);
        return URI.create(RESOURCE_BASE + subFolder + "/" + hash);
    }

    private void downloadAtlas(URI atlasUri, Path destination, String expectedHash) throws IOException, InterruptedException {
        if (Files.exists(destination)) {
            try {
                if (hashMatches(destination, expectedHash)) {
                    return;
                }
                logger.info("Atlas cache mismatch detected for " + destination.getFileName() + "; refreshing entry.");
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed to verify hash for " + destination + "; attempting to refresh anyway.", ex);
            }
        }

        Files.createDirectories(destination.getParent());
        String body = fetchString(atlasUri);
        Files.writeString(destination, body, StandardCharsets.UTF_8);

        if (!hashMatches(destination, expectedHash)) {
            throw new IOException("Hash mismatch after downloading atlas " + destination);
        }
    }

    private JsonObject fetchJson(URI uri) throws IOException, InterruptedException {
        return JsonParser.parseString(fetchString(uri)).getAsJsonObject();
    }

    private String fetchString(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "sprite-plugin/atlas-cache")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " when fetching " + uri);
        }

        return response.body();
    }

    private boolean hashMatches(Path file, String expectedHash) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            String actual = toHex(digest.digest());
            return expectedHash.equalsIgnoreCase(actual);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 digest unavailable", ex);
        }
    }

    private String toHex(byte[] data) {
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte b : data) {
            builder.append(Character.forDigit((b >>> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }
}
