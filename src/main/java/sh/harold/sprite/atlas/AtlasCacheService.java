package sh.harold.sprite.atlas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import sh.harold.sprite.config.AtlasPopulationMode;
import sh.harold.sprite.config.SpriteConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Fetches atlas definitions from Mojang's services and stores them locally so runtime code can
 * operate without repeated network calls.
 */
public final class AtlasCacheService {
    private static final URI MANIFEST_URI = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final String ATLAS_PATH_SEGMENT = "/atlases/";
    private static final String JSON_SUFFIX = ".json";
    private static final String ASSET_INDEX_FILE = "asset-index.json";

    private final HttpClient httpClient;
    private final Path atlasCacheDir;
    private final Logger logger;
    private final Gson gson;

    public AtlasCacheService(Path dataFolder, Logger logger) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.atlasCacheDir = Objects.requireNonNull(dataFolder, "dataFolder").resolve("atlas-cache");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public JsonObject refreshAtlases(String serverVersion, SpriteConfig config) {
        try {
            Files.createDirectories(atlasCacheDir);
            JsonObject manifestJson = fetchJson(MANIFEST_URI);
            JsonObject versionEntry = findVersionEntry(manifestJson, serverVersion);
            if (versionEntry == null) {
                logger.warning("Unable to find version '" + serverVersion + "' in Mojang manifest; atlas caching skipped.");
                return null;
            }

            URI versionUri = URI.create(versionEntry.get("url").getAsString());
            JsonObject versionJson = fetchJson(versionUri);
            JsonObject assetIndex = versionJson.getAsJsonObject("assetIndex");
            if (assetIndex == null || !assetIndex.has("url")) {
                logger.warning("Version metadata missing asset index information; atlas caching skipped.");
                return null;
            }

            URI assetIndexUri = URI.create(assetIndex.get("url").getAsString());
            JsonObject assetIndexJson = fetchJson(assetIndexUri);
            JsonObject objects = assetIndexJson.getAsJsonObject("objects");
            if (objects == null) {
                logger.warning("Asset index contained no objects; atlas caching skipped.");
                return null;
            }

            if (config.populationMode() == AtlasPopulationMode.AUTOMATIC) {
                populateAtlasesFromClientJar(versionJson, serverVersion);
            } else {
                logger.info("Atlas population mode MANUAL; expecting atlas JSON files under " + atlasCacheDir.toAbsolutePath());
            }

            writeAssetIndex(assetIndexJson);
            logger.info("Atlas cache prepared at " + atlasCacheDir.toAbsolutePath());
            return assetIndexJson;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.log(Level.SEVERE, "Failed to cache Minecraft atlases", ex);
            return null;
        }
    }

    public Path getAtlasCacheDir() {
        return atlasCacheDir;
    }

    public JsonObject readStoredAssetIndex() {
        Path assetIndexPath = atlasCacheDir.resolve(ASSET_INDEX_FILE);
        if (!Files.exists(assetIndexPath)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(assetIndexPath, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to read cached asset index", ex);
            return null;
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

    private JsonObject fetchJson(URI uri) throws IOException, InterruptedException {
        return JsonParser.parseString(fetchString(uri)).getAsJsonObject();
    }

    private String fetchString(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", "sprite-plugin/atlas-cache")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " when fetching " + uri);
        }
        return response.body();
    }

    private void writeAssetIndex(JsonObject assetIndexJson) throws IOException {
        Path assetIndexPath = atlasCacheDir.resolve(ASSET_INDEX_FILE);
        Files.createDirectories(assetIndexPath.getParent());
        Files.writeString(assetIndexPath, gson.toJson(assetIndexJson), StandardCharsets.UTF_8);
    }

    private void populateAtlasesFromClientJar(JsonObject versionJson, String serverVersion) throws IOException, InterruptedException {
        JsonObject downloads = versionJson.getAsJsonObject("downloads");
        if (downloads == null || !downloads.has("client")) {
            logger.warning("Version JSON missing client download information; cannot populate atlases automatically.");
            return;
        }
        JsonObject client = downloads.getAsJsonObject("client");
        if (!client.has("url") || !client.has("sha1")) {
            logger.warning("Client download metadata incomplete; cannot populate atlases automatically.");
            return;
        }

        String jarUrl = client.get("url").getAsString();
        String expectedSha = client.get("sha1").getAsString();

        Path jarCacheDir = atlasCacheDir.resolve("jar-cache");
        Files.createDirectories(jarCacheDir);
        Path jarPath = jarCacheDir.resolve(serverVersion + ".jar");

        if (Files.notExists(jarPath) || !hashMatches(jarPath, expectedSha)) {
            logger.info("Downloading Minecraft client jar for " + serverVersion);
            downloadJar(URI.create(jarUrl), jarPath);
            if (!hashMatches(jarPath, expectedSha)) {
                throw new IOException("Downloaded jar failed SHA-1 verification.");
            }
        } else {
            logger.info("Reusing cached Minecraft client jar for " + serverVersion);
        }

        AtlasCacheMetadata metadata = readMetadata();
        if (metadata != null
            && metadata.version().equals(serverVersion)
            && metadata.jarSha1().equalsIgnoreCase(expectedSha)) {
            logger.info("Atlas cache already up to date for " + serverVersion + "; skipping extraction.");
            return;
        }

        int extracted = extractAtlasesFromJar(jarPath);
        writeMetadata(new AtlasCacheMetadata(serverVersion, expectedSha, System.currentTimeMillis()));
        logger.info("Extracted " + extracted + " atlas files from client jar.");
    }

    private void downloadJar(URI uri, Path target) throws IOException, InterruptedException {
        Path tempFile = Files.createTempFile("sprite-client", ".jar");
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "sprite-plugin/atlas-client")
                .GET()
                .build();
            HttpResponse<Path> response = httpClient.send(request, BodyHandlers.ofFile(tempFile));
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " when downloading client jar.");
            }
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private int extractAtlasesFromJar(Path jarPath) throws IOException {
        int extracted = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jarPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (!name.startsWith("assets/") || !name.contains(ATLAS_PATH_SEGMENT) || !name.endsWith(JSON_SUFFIX)) {
                    continue;
                }
                String relative = name.substring("assets/".length());
                Path destination = atlasCacheDir.resolve(relative);
                Files.createDirectories(destination.getParent());
                Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
                extracted++;
            }
        }
        return extracted;
    }

    private AtlasCacheMetadata readMetadata() {
        Path metadataPath = atlasCacheDir.resolve("atlas-metadata.json");
        if (!Files.exists(metadataPath)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(metadataPath, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return new AtlasCacheMetadata(
                json.get("version").getAsString(),
                json.get("jarSha1").getAsString(),
                json.get("extractedAt").getAsLong()
            );
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to read atlas metadata; cache will be re-extracted.", ex);
            return null;
        }
    }

    private void writeMetadata(AtlasCacheMetadata metadata) throws IOException {
        Path metadataPath = atlasCacheDir.resolve("atlas-metadata.json");
        Files.writeString(metadataPath, gson.toJson(metadata.toJson()), StandardCharsets.UTF_8);
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

    private record AtlasCacheMetadata(String version, String jarSha1, long extractedAt) {
        JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("version", version);
            obj.addProperty("jarSha1", jarSha1);
            obj.addProperty("extractedAt", extractedAt);
            return obj;
        }
    }
}
