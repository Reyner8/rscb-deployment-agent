package com.rscb.agent.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiClientTest {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private ApiClient client;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastApiKey = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new ApiClient(base, java.time.Duration.ofSeconds(5));

        server.createContext("/api/v1/agents/register", exchange -> respond(exchange, 200,
                "{\"success\":true,\"data\":{\"deviceId\":\"device-1\",\"token\":\"token-abc\"}}"));
        server.createContext("/api/v1/agents/heartbeat", exchange -> respond(exchange, 200,
                "{\"success\":true,\"data\":{\"status\":\"ok\"}}"));
        server.createContext("/api/v1/agents/updates", exchange -> respond(exchange, 200,
                "{\"success\":true,\"data\":{\"hasUpdate\":true,\"latestVersion\":\"1.5.0\","
                        + "\"downloadUrl\":\"/api/v1/agents/artifacts/rel-1/download-url\","
                        + "\"deploymentId\":\"dep-1\",\"targetVersion\":\"1.5.0\",\"deploymentStatus\":\"ASSIGNED\"}}"));
        server.createContext("/api/v1/agents/deployments/dep-1/status", exchange -> respond(exchange, 200,
                "{\"success\":true,\"data\":{\"status\":\"ok\"}}"));
        server.createContext("/api/v1/agents/artifacts/rel-1/download-url", exchange -> respond(exchange, 200,
                "{\"success\":true,\"data\":{\"downloadUrl\":\"/api/v1/agents/artifacts/rel-1/file\","
                        + "\"fileName\":\"simrs-1.5.0.zip\",\"size\":4,\"sha256\":\"" + "a".repeat(64) + "\"}}"));
        server.createContext("/api/v1/agents/artifacts/rel-1/file", exchange -> {
            record(exchange);
            byte[] payload = "ZIP!".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        record(exchange);
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private void record(HttpExchange exchange) {
        lastPath.set(exchange.getRequestURI().getPath());
        lastApiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
        try {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            lastBody.set(null);
        }
    }

    @Test
    void registerMengirimFieldSesuaiKontrakDanMemparsingToken() throws IOException {
        Dto.RegisterResponse response = client.register(new Dto.RegisterRequest(
                "device-1", "unit-01", List.of("10.0.0.5"), "Windows 11 x64", "0.1.0", "1.4.9"));

        assertEquals("/api/v1/agents/register", lastPath.get());
        assertNull(lastApiKey.get());
        assertTrue(lastBody.get().contains("\"deviceId\":\"device-1\""));
        assertTrue(lastBody.get().contains("\"ipAddress\":[\"10.0.0.5\"]"));
        assertEquals("device-1", response.deviceId());
        assertEquals("token-abc", response.token());
    }

    @Test
    void heartbeatTerautentikasiDenganHeaderXApiKey() throws IOException {
        client.heartbeat("token-abc", new Dto.HeartbeatRequest("unit-01", null, "0.1.0", "1.5.0"));

        assertEquals("/api/v1/agents/heartbeat", lastPath.get());
        assertEquals("token-abc", lastApiKey.get());
    }

    @Test
    void getUpdatesMemparsingSeluruhField() throws IOException {
        Dto.UpdatesResponse updates = client.getUpdates("token-abc");

        assertTrue(updates.hasUpdate());
        assertEquals("1.5.0", updates.latestVersion());
        assertEquals("/api/v1/agents/artifacts/rel-1/download-url", updates.downloadUrl());
        assertEquals("dep-1", updates.deploymentId());
    }

    @Test
    void status401MenghasilkanAuthTokenInvalidException() {
        server.createContext("/api/v1/agents/hilang", exchange -> respond(exchange, 401,
                "{\"success\":false,\"statusCode\":401,\"message\":\"Invalid device token\"}"));

        ApiException e = assertThrows(ApiException.class,
                () -> client.send("GET", client.resolveUri("/api/v1/agents/hilang"), "t", null));

        assertTrue(e instanceof AuthTokenInvalidException);
        assertEquals(401, e.getStatusCode());
    }

    @Test
    void errorResponseMengekstrakMessageDariBody() {
        server.createContext("/api/v1/agents/error", exchange -> respond(exchange, 409,
                "{\"success\":false,\"statusCode\":409,\"message\":\"Missing parts: 2\"}"));

        ApiException e = assertThrows(ApiException.class,
                () -> client.send("GET", client.resolveUri("/api/v1/agents/error"), "t", null));

        assertEquals(409, e.getStatusCode());
        assertEquals("Missing parts: 2", e.getMessage());
    }

    @Test
    void getDownloadInfoMemparsingMetadataChecksum() throws IOException {
        Dto.DownloadInfo info = client.getDownloadInfo("token-abc",
                "/api/v1/agents/artifacts/rel-1/download-url");

        assertEquals("/api/v1/agents/artifacts/rel-1/file", info.downloadUrl());
        assertEquals("simrs-1.5.0.zip", info.fileName());
        assertEquals(4L, info.size());
        assertEquals("a".repeat(64), info.sha256());
        assertEquals("token-abc", lastApiKey.get());
    }

    @Test
    void downloadJalurLokalMengirimHeaderDeviceDanMenulisFile() throws IOException {
        Path target = tempDir.resolve("downloads/artifact.zip");

        client.download(client.resolveUri("/api/v1/agents/artifacts/rel-1/file"), "token-abc", target);

        assertEquals("ZIP!", Files.readString(target));
        assertEquals("token-abc", lastApiKey.get());
        assertFalse(Files.exists(target.resolveSibling("artifact.zip.part")), "file .part dibersihkan");
    }

    @Test
    void downloadPresignedUrlTanpaHeaderAuth() throws IOException {
        Path target = tempDir.resolve("downloads/presigned.zip");
        String presigned = "http://127.0.0.1:" + server.getAddress().getPort()
                + "/api/v1/agents/artifacts/rel-1/file";

        client.download(URI.create(presigned), null, target);

        assertEquals("ZIP!", Files.readString(target));
        assertNull(lastApiKey.get());
    }

    @Test
    void resolveUriMenggabungkanPathRelatifDenganBase() {
        assertEquals("/api/v1/agents/updates",
                client.resolveUri("/api/v1/agents/updates").getPath());
        assertTrue(client.resolveUri("https://minio.example.com/bucket/x.zip?sig=1").isAbsolute());
    }
}
