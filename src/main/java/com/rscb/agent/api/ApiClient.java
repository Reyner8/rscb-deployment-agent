package com.rscb.agent.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

/**
 * Klien HTTP untuk Deployment API (kontrak /api/v1/agents/*).
 * Autentikasi memakai header {@code x-api-key} berisi token device.
 * URL download yang dikembalikan server bisa:
 * <ul>
 *   <li>path relatif (storage lokal) → diunduh dengan header x-api-key;</li>
 *   <li>URL presigned MinIO → diunduh tanpa header auth.</li>
 * </ul>
 */
public class ApiClient {

    private static final System.Logger LOG = System.getLogger(ApiClient.class.getName());

    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI baseUri;
    private final Duration timeout;

    public ApiClient(String baseUrl, Duration timeout) {
        this.baseUri = URI.create(baseUrl);
        this.timeout = timeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Dto.RegisterResponse register(Dto.RegisterRequest request) throws IOException {
        JsonNode body = send("POST", uri("/api/v1/agents/register"), null, request);
        return mapper.treeToValue(body.get("data"), Dto.RegisterResponse.class);
    }

    public void heartbeat(String token, Dto.HeartbeatRequest request) throws IOException {
        send("POST", uri("/api/v1/agents/heartbeat"), token, request);
    }

    public Dto.UpdatesResponse getUpdates(String token) throws IOException {
        JsonNode body = send("GET", uri("/api/v1/agents/updates"), token, null);
        return mapper.treeToValue(body.get("data"), Dto.UpdatesResponse.class);
    }

    public void reportStatus(String token, String deploymentId, Dto.StatusReportRequest request)
            throws IOException {
        send("POST", uri("/api/v1/agents/deployments/" + deploymentId + "/status"), token, request);
    }

    /** Mengambil info download (URL + fileName + size + sha256). Path relatif dari
     * server di-resolve terhadap baseUrl. */
    public Dto.DownloadInfo getDownloadInfo(String token, String downloadUrlOrPath) throws IOException {
        JsonNode body = send("GET", resolveUri(downloadUrlOrPath), token, null);
        return mapper.treeToValue(body.get("data"), Dto.DownloadInfo.class);
    }

    /**
     * Mengunduh artifact secara streaming ke {@code target}. Bila
     * {@code deviceToken} non-null, request disertai header x-api-key
     * (jalur storage lokal); presigned URL dikirim dengan token null.
     */
    public void download(URI url, String deviceToken, Path target) throws IOException {
        LOG.log(System.Logger.Level.DEBUG, "Mengunduh {0} -> {1}", url, target);
        HttpRequest.Builder builder = HttpRequest.newBuilder(url).timeout(timeout).GET();
        if (deviceToken != null && !deviceToken.isBlank()) {
            builder.header("x-api-key", deviceToken);
        }
        HttpResponse<InputStream> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download terputus", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw httpError(response.statusCode(), body);
        }
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream in = response.body()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ------------------------------------------------------------------

    private URI uri(String path) {
        return resolveUri(path);
    }

    URI resolveUri(String urlOrPath) {
        if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
            return URI.create(urlOrPath);
        }
        String path = urlOrPath.startsWith("/") ? urlOrPath.substring(1) : urlOrPath;
        return baseUri.resolve(path);
    }

    /** Package-private untuk keperluan test. */
    JsonNode send(String method, URI url, String token, Object requestBody) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(url).timeout(timeout);
        switch (method) {
            case "GET" -> builder.GET();
            case "POST" -> {
                String json = requestBody != null
                        ? mapper.writeValueAsString(requestBody)
                        : "{}";
                builder.header("Content-Type", "application/json");
                builder.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            }
            default -> throw new IllegalArgumentException("Method tidak didukung: " + method);
        }
        if (token != null && !token.isBlank()) {
            builder.header("x-api-key", token);
        }

        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request terputus: " + url, e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw httpError(response.statusCode(), response.body());
        }
        try {
            return mapper.readTree(response.body());
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private ApiException httpError(int statusCode, String body) {
        if (statusCode == 401) {
            return new AuthTokenInvalidException(extractMessage(body, "Token device tidak berlaku"));
        }
        return new ApiException(statusCode, extractMessage(body, "HTTP " + statusCode));
    }

    private String extractMessage(String body, String fallback) {
        if (body == null || body.isBlank()) {
            return fallback;
        }
        try {
            JsonNode tree = mapper.readTree(body);
            JsonNode message = tree.get("message");
            if (message != null) {
                if (message.isTextual()) {
                    return message.asText();
                }
                if (message.isArray() && !message.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode item : message) {
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(item.asText());
                    }
                    return sb.toString();
                }
            }
        } catch (IOException ignored) {
            // Body bukan JSON; pakai fallback.
        }
        return fallback;
    }
}
