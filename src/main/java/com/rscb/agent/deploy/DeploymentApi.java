package com.rscb.agent.deploy;

import com.rscb.agent.api.Dto;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Permukaan API yang dibutuhkan mesin deployment, dipisah dari klien HTTP
 * agar DeploymentManager dapat diuji dengan fake.
 */
public interface DeploymentApi {

    Dto.DownloadInfo getDownloadInfo(String token, String downloadUrlOrPath) throws IOException;

    /**
     * Mengunduh artifact. {@code urlOrPath} diteruskan apa adanya (relatif
     * atau absolut); implementasi yang me-resolve path relatif terhadap
     * baseUrl dan memutuskan pengiriman header x-api-key.
     */
    void download(String urlOrPath, String deviceToken, Path target) throws IOException;

    void report(String token, String deploymentId, Dto.StatusReportRequest request) throws IOException;
}
