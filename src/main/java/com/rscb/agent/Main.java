package com.rscb.agent;

import com.rscb.agent.api.ApiClient;
import com.rscb.agent.api.ApiException;
import com.rscb.agent.api.AuthTokenInvalidException;
import com.rscb.agent.api.Dto;
import com.rscb.agent.api.RetryUtil;
import com.rscb.agent.config.AgentConfig;
import com.rscb.agent.config.ConfigLoader;
import com.rscb.agent.deploy.ApiClientDeploymentApi;
import com.rscb.agent.deploy.ChecksumVerifier;
import com.rscb.agent.deploy.DeploymentManager;
import com.rscb.agent.deploy.FileManager;
import com.rscb.agent.deploy.Launcher;
import com.rscb.agent.deploy.ProcessDetector;
import com.rscb.agent.device.DeviceInfoProvider;
import com.rscb.agent.state.AgentState;
import com.rscb.agent.state.StateStore;
import com.rscb.agent.update.UpdateHandler;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;
import java.util.logging.FileHandler;
import java.util.logging.LogManager;
import java.util.logging.SimpleFormatter;

/**
 * Titik masuk agent: muat config, pastikan device terdaftar, lalu jalankan
 * dua siklus terjadwal — heartbeat dan cek update (arsitektur.md §9/§11).
 * Kegagalan jaringan di-retry terbatas dan tidak menghentikan loop; SIMRS
 * di komputer unit tidak boleh terdampak oleh server yang mati
 * (arsitektur.md §21).
 */
public final class Main {

    private static final System.Logger LOG = System.getLogger(Main.class.getName());

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Path configPath = resolveConfigPath(args).toAbsolutePath();
        AgentConfig config = ConfigLoader.load(configPath);
        setupLogging(configPath.getParent().resolve("logs"));
        LOG.log(Level.INFO, "RSCB Deployment Agent v{0} — server={1}, aplikasi={2}",
                AgentVersion.VERSION, config.server().baseUrl(), config.application().path());

        StateStore stateStore = new StateStore(configPath.getParent().resolve("agent-state.json"));
        AgentState state = stateStore.load();
        ApiClient api = new ApiClient(
                config.server().baseUrl(),
                Duration.ofSeconds(config.server().timeoutSeconds()));
        DeviceInfoProvider deviceInfo = new DeviceInfoProvider();

        ensureRegistered(api, state, stateStore, config, deviceInfo);

        UpdateHandler updateHandler = new DeploymentManager(
                new ApiClientDeploymentApi(api),
                stateStore,
                state,
                config,
                ProcessDetector.create(),
                new FileManager(Path.of(config.application().path())),
                new ChecksumVerifier(),
                Launcher.DEFAULT);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdownNow));

        Runnable heartbeat = () -> safe("heartbeat", () ->
                heartbeatOnce(api, state, stateStore, config, deviceInfo));
        Runnable updateCheck = () -> safe("cek update", () ->
                updateOnce(api, state, stateStore, config, deviceInfo, updateHandler));

        scheduleJittered(scheduler, heartbeat, config.intervals().heartbeatSeconds(), 10);
        scheduleJittered(scheduler, updateCheck, config.intervals().updateCheckSeconds(), 30);
    }

    static void ensureRegistered(ApiClient api, AgentState state, StateStore stateStore,
                                 AgentConfig config, DeviceInfoProvider deviceInfo) throws IOException {
        if (state.isRegistered()) {
            return;
        }
        register(api, state, stateStore, config, deviceInfo, UUID.randomUUID().toString());
    }

    /** Register ulang dengan deviceId yang sama — token di server dirotasi
     * dan token baru disimpan. Dipanggil saat 401. */
    private static void reRegister(ApiClient api, AgentState state, StateStore stateStore,
                                   AgentConfig config, DeviceInfoProvider deviceInfo) throws IOException {
        LOG.log(Level.WARNING, "Token tidak berlaku, register ulang dengan deviceId {0}", state.getDeviceId());
        register(api, state, stateStore, config, deviceInfo, state.getDeviceId());
    }

    private static void register(ApiClient api, AgentState state, StateStore stateStore,
                                 AgentConfig config, DeviceInfoProvider deviceInfo,
                                 String deviceId) throws IOException {
        Dto.RegisterRequest request = new Dto.RegisterRequest(
                deviceId,
                deviceInfo.hostname(),
                deviceInfo.ipAddresses(),
                deviceInfo.os(),
                AgentVersion.VERSION,
                state.getCurrentVersion());
        try {
            Dto.RegisterResponse response = api.register(request);
            state.setDeviceId(response.deviceId());
            state.setToken(response.token());
            stateStore.save(state);
            LOG.log(Level.INFO, "Device terdaftar sebagai {0}", response.deviceId());
        } catch (IOException e) {
            // Server belum bisa dihubungi: jangan matikan agent, coba lagi
            // pada siklus berikutnya (arsitektur.md §17 Server Offline).
            LOG.log(Level.WARNING, "Registrasi gagal, akan dicoba ulang: {0}", e.getMessage());
        }
    }

    private static void heartbeatOnce(ApiClient api, AgentState state, StateStore stateStore,
                                      AgentConfig config, DeviceInfoProvider deviceInfo) throws Exception {
        try {
            RetryUtil.withRetry(() -> {
                api.heartbeat(state.getToken(), new Dto.HeartbeatRequest(
                        deviceInfo.hostname(),
                        deviceInfo.ipAddresses(),
                        AgentVersion.VERSION,
                        state.getCurrentVersion()));
                return Boolean.TRUE;
            }, 3, 1000, "heartbeat");
        } catch (AuthTokenInvalidException e) {
            reRegister(api, state, stateStore, config, deviceInfo);
        }
    }

    private static void updateOnce(ApiClient api, AgentState state, StateStore stateStore,
                                   AgentConfig config, DeviceInfoProvider deviceInfo,
                                   UpdateHandler updateHandler) throws Exception {
        Dto.UpdatesResponse updates;
        try {
            updates = RetryUtil.withRetry(
                    () -> api.getUpdates(state.getToken()), 3, 1000, "cek update");
        } catch (AuthTokenInvalidException e) {
            reRegister(api, state, stateStore, config, deviceInfo);
            updates = api.getUpdates(state.getToken());
        }
        updateHandler.handle(updates);
    }

    private static void scheduleJittered(ScheduledExecutorService scheduler, Runnable task,
                                         int periodSeconds, int jitterSeconds) {
        AtomicLong delay = new AtomicLong(0);
        Runnable wrapper = new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } finally {
                    long jitter = RandomGenerator.getDefault().nextLong(jitterSeconds + 1);
                    delay.set(periodSeconds + jitter);
                    scheduler.schedule(this, delay.get(), TimeUnit.SECONDS);
                }
            }
        };
        scheduler.schedule(wrapper, 0, TimeUnit.SECONDS);
    }

    private static void safe(String label, IoTask task) {
        try {
            task.run();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "{0} gagal (dilewati, dicoba lagi pada siklus berikutnya): {1}",
                    label, e.getMessage());
        }
    }

    @FunctionalInterface
    interface IoTask {
        void run() throws Exception;
    }

    /** Log ke console + file berputar (3 x 5 MB) di folder logs sebelah config. */
    private static void setupLogging(Path logsDir) {
        try {
            Files.createDirectories(logsDir);
            java.util.logging.Logger root = LogManager.getLogManager().getLogger("");
            FileHandler fileHandler = new FileHandler(
                    logsDir.resolve("agent.log").toString(), 5_000_000, 3, true);
            fileHandler.setFormatter(new SimpleFormatter());
            root.addHandler(fileHandler);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Logging ke file tidak dapat dibuka, hanya console: {0}", e.getMessage());
        }
    }

    static Path resolveConfigPath(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--config".equals(args[i])) {
                return Path.of(args[i + 1]);
            }
        }
        String programData = System.getenv("PROGRAMDATA");
        if (programData != null) {
            Path windowsDefault = Path.of(programData, "RSCB", "DeploymentAgent", "config.yml");
            if (Files.isRegularFile(windowsDefault)) {
                return windowsDefault;
            }
        }
        return Path.of("config.yml");
    }
}
