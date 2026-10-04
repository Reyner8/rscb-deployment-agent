package com.rscb.agent.deploy;

import com.rscb.agent.api.Dto;
import com.rscb.agent.api.RetryUtil;
import com.rscb.agent.config.AgentConfig;
import com.rscb.agent.state.AgentState;
import com.rscb.agent.state.StateStore;
import com.rscb.agent.update.UpdateHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orkestrator deployment (arsitektur.md §8–§9, design-patterns.md §23):
 *
 * <pre>
 * DOWNLOADING → VERIFYING → (WAITING) → INSTALLING → STARTING → SUCCESS
 *      \              \           \            \            \
 *       └──────────────┴───────────┴────────────┴────────────┴──► FAILED
 * </pre>
 *
 * Aturan yang dipegang:
 * - artifact dengan checksum/ukuran tidak cocok tidak pernah menyentuh instalasi;
 * - SIMRS yang sedang berjalan tidak pernah dipaksa mati — agent masuk WAITING;
 * - kegagalan instalasi/start mengembalikan instalasi sebelumnya dari backup;
 * - deployment yang sama tidak diinstal ulang bila sudah sukses (idempotency §27);
 * - kegagalan laporan status ke server tidak menghentikan proses lokal.
 */
public class DeploymentManager implements UpdateHandler {

    private static final System.Logger LOG = System.getLogger(DeploymentManager.class.getName());
    private static final Set<String> TERMINAL_STATUSES = Set.of("SUCCESS", "FAILED", "CANCELLED");

    private final DeploymentApi api;
    private final StateStore stateStore;
    private final AgentState state;
    private final AgentConfig config;
    private final ProcessDetector processDetector;
    private final FileManager files;
    private final ChecksumVerifier checksum;
    private final Launcher launcher;
    private final AtomicBoolean deploying = new AtomicBoolean(false);

    /** Jeda sebelum verifikasi proses setelah start (diuji dengan nilai kecil). */
    long startVerifyDelayMs = 5000;

    public DeploymentManager(DeploymentApi api, StateStore stateStore, AgentState state,
                             AgentConfig config, ProcessDetector processDetector,
                             FileManager files, ChecksumVerifier checksum, Launcher launcher) {
        this.api = api;
        this.stateStore = stateStore;
        this.state = state;
        this.config = config;
        this.processDetector = processDetector;
        this.files = files;
        this.checksum = checksum;
        this.launcher = launcher;
    }

    @Override
    public void handle(Dto.UpdatesResponse updates) {
        if (!updates.hasUpdate()) {
            return;
        }
        String deploymentId = updates.deploymentId();
        if (deploymentId == null || deploymentId.isBlank()) {
            LOG.log(System.Logger.Level.INFO,
                    "Update {0} tersedia, menunggu admin membuat deployment", updates.latestVersion());
            return;
        }
        if (updates.deploymentStatus() != null && TERMINAL_STATUSES.contains(updates.deploymentStatus())) {
            return;
        }
        if (!deploying.compareAndSet(false, true)) {
            LOG.log(System.Logger.Level.DEBUG, "Deployment masih berjalan, cek update dilewati");
            return;
        }
        try {
            if (alreadyApplied(deploymentId, updates.targetVersion())) {
                report(deploymentId, "SUCCESS", "Instalasi sudah diterapkan pada siklus sebelumnya", null);
                return;
            }
            execute(deploymentId, updates);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Deployment gagal tak terduga", e);
            fail(deploymentId, "Deployment gagal: " + e.getMessage());
        } finally {
            deploying.set(false);
        }
    }

    private boolean alreadyApplied(String deploymentId, String targetVersion) {
        return deploymentId.equals(state.getLastDeploymentId())
                && "SUCCESS".equals(state.getLastDeploymentResult())
                && targetVersion != null && targetVersion.equals(state.getCurrentVersion());
    }

    private void execute(String deploymentId, Dto.UpdatesResponse updates) {
        String targetVersion = updates.targetVersion() != null ? updates.targetVersion() : "unknown";
        Path appPath = Path.of(config.application().path());
        Path zip = null;
        Path backup = null;
        List<String> installed = List.of();

        try {
            // ---- DOWNLOADING -------------------------------------------------
            report(deploymentId, "DOWNLOADING", "Mengunduh artifact " + targetVersion, null);
            files.cleanupDownloads();
            Dto.DownloadInfo info = downloadInfo(updates.downloadUrl());
            if (info == null || info.downloadUrl() == null || info.downloadUrl().isBlank()) {
                fail(deploymentId, "downloadUrl tidak tersedia di server");
                return;
            }
            zip = files.downloadsDir().resolve("artifact-" + deploymentId + ".zip");
            downloadArtifact(URI.create(info.downloadUrl()), zip);

            // ---- VERIFYING ---------------------------------------------------
            report(deploymentId, "VERIFYING", "Memverifikasi SHA-256 artifact", null);
            try {
                checksum.verify(zip, info.sha256(), info.size());
            } catch (IOException e) {
                deleteQuietly(zip);
                fail(deploymentId, "Artifact tidak valid: " + e.getMessage());
                return;
            }

            // ---- WAITING (SIMRS masih dipakai) --------------------------------
            try {
                if (processDetector.isRunning(appPath)) {
                    report(deploymentId, "WAITING", "Menunggu aplikasi ditutup oleh user", null);
                    Instant deadline = Instant.now()
                            .plus(Duration.ofMinutes(config.deployment().maxWaitMinutes()));
                    while (processDetector.isRunning(appPath)) {
                        if (Instant.now().isAfter(deadline)) {
                            deleteQuietly(zip);
                            fail(deploymentId, "Aplikasi tidak ditutup dalam batas waktu "
                                    + config.deployment().maxWaitMinutes() + " menit");
                            return;
                        }
                        sleep(config.deployment().waitPollSeconds() * 1000L);
                    }
                }
            } catch (IOException e) {
                // Deteksi tidak dapat dipercaya → jangan berani mengganti file
                // bila aplikasi bisa saja masih berjalan.
                deleteQuietly(zip);
                fail(deploymentId, "Deteksi proses gagal: " + e.getMessage());
                return;
            }

            // ---- INSTALLING + STARTING ----------------------------------------
            report(deploymentId, "INSTALLING", "Memasang versi " + targetVersion, null);
            try {
                Path staged = files.unzip(zip, sanitize(targetVersion));
                backup = files.backupCurrent();
                installed = files.installStaged(staged);
                report(deploymentId, "STARTING", "Menjalankan aplikasi", null);
                launcher.start(appPath, config.application().launcher());
            } catch (Exception e) {
                if (backup != null) {
                    try {
                        files.restoreBackup(backup, installed);
                    } catch (IOException restoreError) {
                        LOG.log(System.Logger.Level.ERROR, "Restore backup gagal", restoreError);
                    }
                }
                deleteQuietly(zip);
                fail(deploymentId, "Instalasi gagal, instalasi sebelumnya dikembalikan: " + e.getMessage());
                return;
            }

            // ---- verifikasi start ----------------------------------------------
            sleep(startVerifyDelayMs);
            boolean running;
            try {
                running = processDetector.isRunning(appPath);
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "Verifikasi proses gagal: {0}", e.getMessage());
                running = true; // tidak pasti; jangan gagalkan instalasi hanya karena deteksi gagal
            }
            if (!running) {
                deleteQuietly(zip);
                fail(deploymentId, "Aplikasi tidak terdeteksi berjalan setelah start");
                return;
            }

            // ---- SUCCESS --------------------------------------------------------
            state.setCurrentVersion(targetVersion);
            state.setLastDeploymentId(deploymentId);
            state.setLastDeploymentResult("SUCCESS");
            state.setLastDeploymentVersion(targetVersion);
            stateStore.save(state);
            deleteQuietly(zip);
            try {
                files.pruneBackups(config.backup().keep());
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "Prune backup gagal: {0}", e.getMessage());
            }
            report(deploymentId, "SUCCESS", "Instalasi versi " + targetVersion + " selesai", null);
            LOG.log(System.Logger.Level.INFO, "Deployment {0} sukses: versi {1}", deploymentId, targetVersion);
        } catch (Exception e) {
            deleteQuietly(zip);
            fail(deploymentId, "Deployment gagal: " + e.getMessage());
        }
    }

    /** Mengambil metadata download (URL + checksum) dengan bounded retry. */
    private Dto.DownloadInfo downloadInfo(String downloadUrlOrPath) throws Exception {
        return RetryUtil.withRetry(
                () -> api.getDownloadInfo(state.getToken(), downloadUrlOrPath),
                3, 2000, "ambil info download");
    }

    /**
     * Mengunduh artifact dengan bounded retry — unduhan utuh diulang saat
     * koneksi terputus. URL presigned (absolute) diunduh tanpa header auth;
     * path relatif (storage lokal) diunduh dengan token device.
     */
    private void downloadArtifact(URI url, Path target) throws Exception {
        RetryUtil.withRetry(() -> {
            api.download(url, url.isAbsolute() ? null : state.getToken(), target);
            return Boolean.TRUE;
        }, 3, 2000, "unduh artifact");
    }

    private void fail(String deploymentId, String errorMessage) {
        LOG.log(System.Logger.Level.WARNING, "Deployment {0} gagal: {1}", deploymentId, errorMessage);
        report(deploymentId, "FAILED", null, errorMessage);
    }

    /** Laporan status tidak boleh menghentikan proses lokal bila server sulit dihubungi. */
    private void report(String deploymentId, String status, String message, String errorMessage) {
        try {
            RetryUtil.withRetry(() -> {
                api.report(state.getToken(), deploymentId,
                        new Dto.StatusReportRequest(status, message, errorMessage));
                return Boolean.TRUE;
            }, 3, 1000, "lapor status " + status);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Laporan status {0} gagal terkirim: {1}",
                    status, e.getMessage());
        }
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // dibersihkan lagi pada cleanupDownloads
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread terputus saat menunggu", e);
        }
    }
}
