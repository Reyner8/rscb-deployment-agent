package com.rscb.agent.deploy;

import com.rscb.agent.api.Dto;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * Permukaan API yang dibutuhkan mesin deployment, dipisah dari klien HTTP
 * agar DeploymentManager dapat diuji dengan fake.
 */
public interface DeploymentApi {

    Dto.DownloadInfo getDownloadInfo(String token, String downloadUrlOrPath) throws IOException;

    /** Mengunduh artifact; token dikirim hanya bila URL relatif (storage lokal). */
    void download(URI url, String deviceToken, Path target) throws IOException;

    void report(String token, String deploymentId, Dto.StatusReportRequest request) throws IOException;
}
