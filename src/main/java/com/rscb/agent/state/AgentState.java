package com.rscb.agent.state;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * State lokal agent yang persisten di disk. Menyimpan identitas device
 * (deviceId + token dari registrasi), versi terpasang saat ini, dan hasil
 * deployment terakhir untuk kebutuhan idempotency (design-patterns.md §27).
 */
public class AgentState {

    private String deviceId;
    private String token;
    private String currentVersion;
    private String lastDeploymentId;
    private String lastDeploymentResult;
    private String lastDeploymentVersion;
    private String updatedAt;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getLastDeploymentId() {
        return lastDeploymentId;
    }

    public void setLastDeploymentId(String lastDeploymentId) {
        this.lastDeploymentId = lastDeploymentId;
    }

    public String getLastDeploymentResult() {
        return lastDeploymentResult;
    }

    public void setLastDeploymentResult(String lastDeploymentResult) {
        this.lastDeploymentResult = lastDeploymentResult;
    }

    public String getLastDeploymentVersion() {
        return lastDeploymentVersion;
    }

    public void setLastDeploymentVersion(String lastDeploymentVersion) {
        this.lastDeploymentVersion = lastDeploymentVersion;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @JsonIgnore
    public boolean isRegistered() {
        return deviceId != null && !deviceId.isBlank() && token != null && !token.isBlank();
    }
}
