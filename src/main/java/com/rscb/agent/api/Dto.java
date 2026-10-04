package com.rscb.agent.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** DTO request/response untuk Deployment API. Field harus persis sesuai
 * kontrak backend (ValidationPipe menolak field asing). */
public final class Dto {

    private Dto() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RegisterRequest(
            String deviceId,
            String hostname,
            List<String> ipAddress,
            String os,
            String agentVersion,
            String applicationVersion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RegisterResponse(String deviceId, String token) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HeartbeatRequest(
            String hostname,
            List<String> ipAddress,
            String agentVersion,
            String applicationVersion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdatesResponse(
            boolean hasUpdate,
            String latestVersion,
            String downloadUrl,
            String deploymentId,
            String targetVersion,
            String deploymentStatus) {
    }

    /** Response GET /agents/artifacts/:id/download-url. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DownloadInfo(String downloadUrl, String fileName, Long size, String sha256) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StatusReportRequest(String status, String message, String errorMessage) {
    }
}
