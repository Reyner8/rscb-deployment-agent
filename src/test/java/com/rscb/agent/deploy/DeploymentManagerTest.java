package com.rscb.agent.deploy;

import com.rscb.agent.api.Dto;
import com.rscb.agent.config.AgentConfig;
import com.rscb.agent.state.AgentState;
import com.rscb.agent.state.StateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentManagerTest {

    @TempDir
    Path root;

    private Path appPath;
    private StateStore stateStore;
    private AgentState state;
    private AgentConfig config;
    private FakeApi api;
    private FileManager files;
    private ChecksumVerifier checksum;
    private AtomicInteger detectorCalls;
    private boolean appRunning;
    private java.util.concurrent.atomic.AtomicBoolean started;
    private Launcher throwingLauncher;
    private byte[] zipPayload;

    @BeforeEach
    void setUp() throws IOException {
        appPath = root.resolve("app");
        Files.createDirectories(appPath);
        Files.writeString(appPath.resolve("application.jar"), "versi-lama");

        stateStore = new StateStore(root.resolve("state.json"));
        state = new AgentState();
        state.setDeviceId("device-1");
        state.setToken("token-abc");
        state.setCurrentVersion("1.4.9");

        config = new AgentConfig(
                new AgentConfig.Server("http://test", 5),
                new AgentConfig.Intervals(60, 300),
                new AgentConfig.Application(appPath.toString(), "run.bat"),
                new AgentConfig.Deployment(1, 0),
                new AgentConfig.Backup(1));

        api = new FakeApi();
        files = new FileManager(appPath);
        checksum = new ChecksumVerifier();
        detectorCalls = new AtomicInteger();
        appRunning = false;
        started = new java.util.concurrent.atomic.AtomicBoolean(false);
        throwingLauncher = (path, launcher) -> {
            throw new IOException("launcher gagal simulasi");
        };
        zipPayload = zipBytes(
                "application.jar", "versi-baru",
                "run.bat", "@echo off");
        api.info = new Dto.DownloadInfo(
                "/api/v1/agents/artifacts/rel-1/file",
                "simrs-2.0.0.zip",
                (long) zipPayload.length,
                sha256(zipPayload));
        api.payload = zipPayload;
    }

    private DeploymentManager newManager(Launcher launcher, ProcessDetector detector) {
        DeploymentManager manager = new DeploymentManager(
                api, stateStore, state, config, detector, files, checksum, launcher);
        manager.startVerifyDelayMs = 0;
        return manager;
    }

    private ProcessDetector detector() {
        return path -> {
            detectorCalls.incrementAndGet();
            return appRunning || started.get();
        };
    }

    private static byte[] zipBytes(String... entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < entries.length; i += 2) {
                zip.putNextEntry(new ZipEntry(entries[i]));
                zip.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private String sha256(byte[] data) throws IOException {
        Path tmp = root.resolve("tmp-check");
        Files.write(tmp, data);
        return new ChecksumVerifier().sha256(tmp);
        // file sementara dibersihkan oleh temp dir
    }

    private Dto.UpdatesResponse updates(String deploymentId, String status) {
        return new Dto.UpdatesResponse(
                true, "2.0.0", "/api/v1/agents/artifacts/rel-1/download-url",
                deploymentId, "2.0.0", status);
    }

    @Test
    void alurLengkapBerhasilDenganUrutanStatusYangBenar() throws IOException {
        DeploymentManager manager = newManager((path, launcher) -> started.set(true), detector());

        manager.handle(updates("dep-1", "ASSIGNED"));

        assertEquals(
                List.of("DOWNLOADING", "VERIFYING", "INSTALLING", "STARTING", "SUCCESS"),
                api.statusSequence);
        assertEquals("2.0.0", state.getCurrentVersion());
        assertEquals("dep-1", state.getLastDeploymentId());
        assertEquals("SUCCESS", state.getLastDeploymentResult());
        assertEquals("versi-baru", Files.readString(appPath.resolve("application.jar")));
        assertTrue(Files.exists(appPath.resolve("run.bat")));
        assertFalse(Files.exists(files.downloadsDir().resolve("artifact-dep-1.zip")),
                "zip sementara dibersihkan setelah sukses");
    }

    @Test
    void checksumMismatchGagalkanDeploymentTanpaMenyentuhInstalasi() throws IOException {
        api.info = new Dto.DownloadInfo(api.info.downloadUrl(), api.info.fileName(),
                api.info.size(), "0".repeat(64));

        newManager((path, launcher) -> { }, detector()).handle(updates("dep-1", "ASSIGNED"));

        assertEquals(List.of("DOWNLOADING", "VERIFYING", "FAILED"), api.statusSequence);
        assertEquals("versi-lama", Files.readString(appPath.resolve("application.jar")));
        assertNullVersionUntouched();
    }

    @Test
    void aplikasiBerjalanMasukWAITINGLaluLanjutSetelahDitutup() throws IOException {
        appRunning = true;
        ProcessDetector closesAfterFirstCheck = path -> {
            int call = detectorCalls.incrementAndGet();
            // dua pemanggilan pertama: masih berjalan; setelahnya dianggap
            // ditutup user (atau sudah distart ulang oleh agent).
            return call <= 2 || started.get();
        };

        newManager((path, launcher) -> started.set(true), closesAfterFirstCheck)
                .handle(updates("dep-1", "ASSIGNED"));

        assertEquals(
                List.of("DOWNLOADING", "VERIFYING", "WAITING", "INSTALLING", "STARTING", "SUCCESS"),
                api.statusSequence);
        assertEquals("versi-baru", Files.readString(appPath.resolve("application.jar")));
    }

    @Test
    void payloadRusakSaatUnzipMemicuFAILEDDanInstalasiTetapUtuh() throws IOException {
        api.payload = "ini bukan zip".getBytes(StandardCharsets.UTF_8);
        api.info = new Dto.DownloadInfo(api.info.downloadUrl(), api.info.fileName(),
                (long) api.payload.length, sha256Unchecked(api.payload));

        newManager((path, launcher) -> { }, detector()).handle(updates("dep-1", "ASSIGNED"));

        assertEquals(List.of("DOWNLOADING", "VERIFYING", "INSTALLING", "FAILED"), api.statusSequence);
        assertEquals("versi-lama", Files.readString(appPath.resolve("application.jar")));
    }

    @Test
    void launcherGagalMengembalikanInstalasiSebelumnya() throws IOException {
        newManager(throwingLauncher, detector()).handle(updates("dep-1", "ASSIGNED"));

        assertEquals(
                List.of("DOWNLOADING", "VERIFYING", "INSTALLING", "STARTING", "FAILED"),
                api.statusSequence);
        assertEquals("versi-lama", Files.readString(appPath.resolve("application.jar")));
        assertTrue(api.lastErrorMessage.contains("Instalasi gagal"));
    }

    @Test
    void deploymentYangSudahSuksesTidakDiinstalUlang() throws IOException {
        state.setCurrentVersion("2.0.0");
        state.setLastDeploymentId("dep-1");
        state.setLastDeploymentResult("SUCCESS");
        state.setLastDeploymentVersion("2.0.0");

        newManager((path, launcher) -> { }, detector()).handle(updates("dep-1", "ASSIGNED"));

        assertEquals(List.of("SUCCESS"), api.statusSequence);
        assertEquals("versi-lama", Files.readString(appPath.resolve("application.jar")),
                "tidak ada instalasi ulang");
    }

    @Test
    void tanpaDeploymentIdAtauStatusTerminalTidakAdaAksi() {
        newManager((path, launcher) -> { }, detector())
                .handle(new Dto.UpdatesResponse(true, "2.0.0", "/x", null, "2.0.0", null));
        newManager((path, launcher) -> { }, detector())
                .handle(updates("dep-1", "SUCCESS"));

        assertTrue(api.statusSequence.isEmpty());
    }

    private void assertNullVersionUntouched() {
        assertEquals("1.4.9", state.getCurrentVersion(), "versi lokal tidak berubah");
    }

    private static String sha256Unchecked(byte[] data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest(data)) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Fake DeploymentApi: mencatat laporan status dan menulis payload ke file target. */
    private class FakeApi implements DeploymentApi {
        Dto.DownloadInfo info;
        byte[] payload;
        final List<String> statusSequence = new ArrayList<>();
        String lastErrorMessage;

        @Override
        public Dto.DownloadInfo getDownloadInfo(String token, String downloadUrlOrPath) {
            return info;
        }

        @Override
        public void download(URI url, String deviceToken, Path target) throws IOException {
            Files.write(target, payload);
        }

        @Override
        public void report(String token, String deploymentId, Dto.StatusReportRequest request) {
            statusSequence.add(request.status());
            if (request.errorMessage() != null) {
                lastErrorMessage = request.errorMessage();
            }
        }
    }
}
