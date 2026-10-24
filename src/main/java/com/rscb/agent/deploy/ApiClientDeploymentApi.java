package com.rscb.agent.deploy;

import com.rscb.agent.api.ApiClient;
import com.rscb.agent.api.Dto;

import java.io.IOException;
import java.nio.file.Path;

/** Adapter {@link DeploymentApi} di atas {@link ApiClient} untuk dipakai Main. */
public class ApiClientDeploymentApi implements DeploymentApi {

    private final ApiClient client;

    public ApiClientDeploymentApi(ApiClient client) {
        this.client = client;
    }

    @Override
    public Dto.DownloadInfo getDownloadInfo(String token, String downloadUrlOrPath) throws IOException {
        return client.getDownloadInfo(token, downloadUrlOrPath);
    }

    @Override
    public void download(String urlOrPath, String deviceToken, Path target) throws IOException {
        client.download(urlOrPath, deviceToken, target);
    }

    @Override
    public void report(String token, String deploymentId, Dto.StatusReportRequest request) throws IOException {
        client.reportStatus(token, deploymentId, request);
    }
}
