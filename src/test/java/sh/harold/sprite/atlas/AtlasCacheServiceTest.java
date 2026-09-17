package sh.harold.sprite.atlas;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlasCacheServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void retriesFailedDownloadsUsingTargetDirectoryForTemporaryStorage() throws Exception {
        byte[] jarBytes = "client-jar".getBytes(StandardCharsets.UTF_8);
        Path cacheDir = tempDir.resolve("jar-cache");
        Path target = cacheDir.resolve("test.jar");
        var requests = new AtomicInteger();
        var targetDirectoryUsed = new AtomicBoolean();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/client.jar", exchange -> {
            int request = requests.incrementAndGet();
            if (request == 1) {
                try (Stream<Path> files = Files.list(cacheDir)) {
                    targetDirectoryUsed.set(files.anyMatch(path -> path.getFileName().toString().endsWith(".part")));
                }
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }

            exchange.sendResponseHeaders(200, jarBytes.length);
            try (var body = exchange.getResponseBody()) {
                body.write(jarBytes);
            }
        });
        server.start();

        try {
            var service = new AtlasCacheService(tempDir, Logger.getLogger(getClass().getName()));
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/client.jar");

            service.downloadJar(uri, target);

            assertEquals(2, requests.get());
            assertTrue(targetDirectoryUsed.get());
            assertArrayEquals(jarBytes, Files.readAllBytes(target));
            try (Stream<Path> files = Files.list(cacheDir)) {
                assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".part")));
            }
        } finally {
            server.stop(0);
        }
    }
}
