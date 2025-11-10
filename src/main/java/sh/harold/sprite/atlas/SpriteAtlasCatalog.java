package sh.harold.sprite.atlas;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Expands Mojang atlas definitions into concrete sprite identifiers that can be referenced
 * by chat components or GUIs.
 */
public final class SpriteAtlasCatalog {
    private static final String ATLAS_TOKEN = "/atlases/";
    private static final String TEXTURE_TOKEN = "/textures/";
    private static final String PNG_SUFFIX = ".png";
    private static final String JSON_SUFFIX = ".json";
    private static final String TEXTURE_INDEX_FILE = "textures.index";
    private static final Pattern NUMERIC_SUFFIX = Pattern.compile("(.+)_\\d+$");

    private final Path cacheRoot;
    private final Logger logger;
    private final AtomicReference<CatalogSnapshot> snapshot;

    public SpriteAtlasCatalog(Path cacheRoot, Logger logger) {
        this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.snapshot = new AtomicReference<>(CatalogSnapshot.empty());
    }

    public Optional<CatalogSnapshot> currentSnapshot() {
        CatalogSnapshot snap = snapshot.get();
        if (snap.atlases().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(snap);
    }

    public CatalogSnapshot snapshotOrEmpty() {
        return snapshot.get();
    }

    public void rebuild(JsonObject assetIndexJson) throws IOException {
        CatalogSnapshot built = buildSnapshot(assetIndexJson);
        snapshot.set(built);
        logger.info("Loaded " + built.atlases().size() + " atlases with " + built.totalSprites() + " sprites.");
    }

    private CatalogSnapshot buildSnapshot(JsonObject assetIndexJson) throws IOException {
        JsonObject objects = assetIndexJson.getAsJsonObject("objects");
        if (objects == null) {
            return CatalogSnapshot.empty();
        }

        var pathToHash = new LinkedHashMap<String, String>();
        for (var entry : objects.entrySet()) {
            var value = entry.getValue().getAsJsonObject();
            if (value.has("hash")) {
                pathToHash.put(entry.getKey(), value.get("hash").getAsString());
            }
        }

        Collection<String> texturePaths = resolveTexturePaths(pathToHash.keySet());
        Map<String, List<String>> texturesByNamespace = buildTextureIndex(texturePaths);

        List<AtlasEntry> atlasEntries = new ArrayList<>();
        for (String path : discoverAtlasPaths()) {
            AtlasEntry entry = buildAtlasEntry(path, texturesByNamespace);
            if (entry != null) {
                atlasEntries.add(entry);
            }
        }

        atlasEntries.sort(Comparator.comparing(AtlasEntry::atlasId));
        Map<String, AtlasEntry> atlasById = new LinkedHashMap<>();
        for (AtlasEntry entry : atlasEntries) {
            atlasById.put(entry.atlasId(), entry);
        }

        int totalSprites = atlasEntries.stream().mapToInt(AtlasEntry::spriteCount).sum();
        return new CatalogSnapshot(Collections.unmodifiableList(atlasEntries),
            Collections.unmodifiableMap(atlasById),
            totalSprites);
    }

    private Collection<String> resolveTexturePaths(Set<String> assetIndexPaths) {
        Path textureIndexPath = cacheRoot.resolve(TEXTURE_INDEX_FILE);
        if (Files.exists(textureIndexPath)) {
            try {
                List<String> entries = Files.readAllLines(textureIndexPath, StandardCharsets.UTF_8);
                List<String> sanitized = new ArrayList<>();
                for (String entry : entries) {
                    String trimmed = entry == null ? "" : entry.trim();
                    if (!trimmed.isEmpty()) {
                        sanitized.add(trimmed.replace('\\', '/'));
                    }
                }
                if (!sanitized.isEmpty()) {
                    return sanitized;
                }
                logger.warning("Texture index file '" + textureIndexPath + "' was empty; falling back to asset index.");
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed to read cached texture index; falling back to asset index.", ex);
            }
        }
        return assetIndexPaths;
    }

    private Map<String, List<String>> buildTextureIndex(Collection<String> paths) {
        var texturesByNamespace = new LinkedHashMap<String, List<String>>();
        for (String path : paths) {
            if (!path.contains(TEXTURE_TOKEN) || !path.endsWith(PNG_SUFFIX)) {
                continue;
            }
            int namespaceEnd = path.indexOf('/');
            if (namespaceEnd <= 0) {
                continue;
            }
            var namespace = path.substring(0, namespaceEnd);
            texturesByNamespace.computeIfAbsent(namespace, key -> new ArrayList<>()).add(path);
        }

        for (List<String> textures : texturesByNamespace.values()) {
            textures.sort(String::compareTo);
        }
        return texturesByNamespace;
    }

    private AtlasEntry buildAtlasEntry(String atlasPath, Map<String, List<String>> texturesByNamespace) throws IOException {
        var namespace = namespaceFromPath(atlasPath);
        var atlasFile = atlasPath.substring(atlasPath.lastIndexOf('/') + 1);
        var atlasName = atlasFile.substring(0, atlasFile.length() - ".json".length());
        var atlasId = namespace + ":" + atlasName;

        Path localFile = cacheRoot.resolve(atlasPath);
        if (!Files.exists(localFile)) {
            logger.warning("Atlas file missing from cache: " + localFile);
            return null;
        }

        JsonObject atlasJson;
        try (var reader = Files.newBufferedReader(localFile, StandardCharsets.UTF_8)) {
            atlasJson = JsonParser.parseReader(reader).getAsJsonObject();
        }
        JsonElement sourcesElement = atlasJson.get("sources");
        if (sourcesElement == null || !sourcesElement.isJsonArray()) {
            logger.warning("Atlas " + atlasId + " had no sources; skipping.");
            return null;
        }

        var groups = new TreeMap<String, GroupBuilder>();
        List<String> textures = texturesByNamespace.getOrDefault(namespace, List.of());

        for (JsonElement sourceElement : sourcesElement.getAsJsonArray()) {
            var source = sourceElement.getAsJsonObject();
            var type = source.get("type").getAsString();
            switch (type) {
                case String t when t.endsWith(":directory") ->
                    expandDirectorySource(namespace, source, textures, groups);
                case String t when t.endsWith(":single") ->
                    expandSingleSource(source, groups);
                case String t when t.endsWith(":paletted_permutations") ->
                    expandPalettedSource(source, groups);
                default ->
                    logger.fine(() -> "Ignoring unsupported atlas source type " + type + " in " + atlasId);
            }
        }

        List<SpriteGroup> spriteGroups = new ArrayList<>();
        for (GroupBuilder builder : groups.values()) {
            spriteGroups.add(builder.build());
        }
        spriteGroups.sort(Comparator.comparing(SpriteGroup::id));

        Map<String, SpriteGroup> groupMap = new LinkedHashMap<>();
        for (SpriteGroup group : spriteGroups) {
            groupMap.put(group.id(), group);
        }

        int spriteCount = spriteGroups.stream().mapToInt(SpriteGroup::size).sum();
        return new AtlasEntry(atlasId, namespace, atlasFile,
            Collections.unmodifiableList(spriteGroups),
            Collections.unmodifiableMap(groupMap),
            spriteCount);
    }

    private List<String> discoverAtlasPaths() throws IOException {
        if (!Files.exists(cacheRoot)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(cacheRoot)) {
            return stream
                .filter(Files::isRegularFile)
                .map(cacheRoot::relativize)
                .map(Path::toString)
                .map(path -> path.replace('\\', '/'))
                .filter(path -> path.contains(ATLAS_TOKEN) && path.endsWith(JSON_SUFFIX))
                .sorted()
                .toList();
        }
    }

    private void expandDirectorySource(
        String namespace,
        JsonObject source,
        List<String> textures,
        Map<String, GroupBuilder> groups
    ) {
        String prefix = source.has("prefix") ? source.get("prefix").getAsString() : "";
        String folder = source.get("source").getAsString();
        String folderPath = normalizeTexturesPath(namespace, folder);

        for (String texturePath : textures) {
            if (!texturePath.startsWith(folderPath)) {
                continue;
            }
            String relativeToFolder = texturePath.substring(folderPath.length());
            if (!relativeToFolder.endsWith(PNG_SUFFIX)) {
                continue;
            }
            String spriteKey = prefix + relativeToFolder.substring(0, relativeToFolder.length() - PNG_SUFFIX.length());
            addSprite(groups, spriteKey);
        }
    }

    private void expandSingleSource(JsonObject source, Map<String, GroupBuilder> groups) {
        String resource = source.get("resource").getAsString();
        String spriteKey = stripNamespace(resource);
        addSprite(groups, spriteKey);
    }

    private void expandPalettedSource(JsonObject source, Map<String, GroupBuilder> groups) {
        JsonObject permutations = source.getAsJsonObject("permutations");
        JsonArray textures = source.getAsJsonArray("textures");
        if (permutations == null || permutations.entrySet().isEmpty() || textures == null) {
            return;
        }

        List<String> suffixes = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : permutations.entrySet()) {
            String name = entry.getKey();
            if (name == null || (name = name.trim()).isEmpty()) {
                continue;
            }
            suffixes.add(name);
        }
        if (suffixes.isEmpty()) {
            return;
        }

        for (JsonElement textureEl : textures) {
            String base = stripNamespace(textureEl.getAsString());
            for (String suffix : suffixes) {
                addSprite(groups, base + "_" + suffix, base);
            }
        }
    }

    private void addSprite(Map<String, GroupBuilder> groups, String spriteKey) {
        addSprite(groups, spriteKey, null);
    }

    private void addSprite(Map<String, GroupBuilder> groups, String spriteKey, String groupOverride) {
        String cleanedKey = spriteKey.replace('\\', '/');
        String groupKey = groupOverride != null ? groupOverride : deriveGroupKey(cleanedKey);
        groups.computeIfAbsent(groupKey, GroupBuilder::new).add(cleanedKey);
    }

    private String deriveGroupKey(String spriteKey) {
        int slash = spriteKey.lastIndexOf('/');
        String directory = slash >= 0 ? spriteKey.substring(0, slash + 1) : "";
        String leaf = slash >= 0 ? spriteKey.substring(slash + 1) : spriteKey;
        Matcher matcher = NUMERIC_SUFFIX.matcher(leaf);
        if (matcher.matches()) {
            return directory + matcher.group(1);
        }
        return spriteKey;
    }

    private String normalizeTexturesPath(String namespace, String folder) {
        String normalizedFolder = folder.startsWith("/") ? folder.substring(1) : folder;
        if (!normalizedFolder.endsWith("/")) {
            normalizedFolder = normalizedFolder + "/";
        }
        return namespace + TEXTURE_TOKEN + normalizedFolder;
    }

    private String stripNamespace(String resource) {
        String value = resource.contains(":") ? resource.substring(resource.indexOf(':') + 1) : resource;
        if (value.endsWith(PNG_SUFFIX)) {
            value = value.substring(0, value.length() - PNG_SUFFIX.length());
        }
        return value;
    }

    private String namespaceFromPath(String path) {
        int slash = path.indexOf('/');
        if (slash <= 0) {
            return "minecraft";
        }
        return path.substring(0, slash);
    }

    public record CatalogSnapshot(List<AtlasEntry> atlases, Map<String, AtlasEntry> atlasMap, int totalSprites) {
        public static CatalogSnapshot empty() {
            return new CatalogSnapshot(List.of(), Map.of(), 0);
        }

        public AtlasEntry atlas(String atlasId) {
            if (atlasId == null || atlasId.isEmpty()) {
                return null;
            }
            AtlasEntry resolved = atlasMap.get(atlasId);
            if (resolved != null) {
                return resolved;
            }
            if (!atlasId.contains(":")) {
                return atlasMap.get("minecraft:" + atlasId);
            }
            return null;
        }
    }

    public record AtlasEntry(
        String atlasId,
        String namespace,
        String fileName,
        List<SpriteGroup> groups,
        Map<String, SpriteGroup> groupMap,
        int spriteCount
    ) {
        public String displayName() {
            return isMinecraft() ? simpleName() : atlasId;
        }

        public String simpleName() {
            if (fileName.endsWith(JSON_SUFFIX)) {
                return fileName.substring(0, fileName.length() - JSON_SUFFIX.length());
            }
            return fileName;
        }

        public boolean isMinecraft() {
            return "minecraft".equals(namespace);
        }

        public SpriteGroup group(String groupId) {
            return groupMap.get(groupId);
        }
    }

    public record SpriteGroup(String id, List<String> sprites) {
        public int size() {
            return sprites.size();
        }
    }

    private record GroupBuilder(String key, Set<String> spriteKeys) {
        private GroupBuilder(String key) {
            this(key, new TreeSet<>());
        }

        private void add(String spriteKey) {
            spriteKeys.add(spriteKey);
        }

        private SpriteGroup build() {
            return new SpriteGroup(key, List.copyOf(spriteKeys));
        }
    }
}
