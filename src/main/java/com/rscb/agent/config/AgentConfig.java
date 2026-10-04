package com.rscb.agent.config;

import java.util.Map;

/**
 * Konfigurasi agent, dimuat dari YAML (lihat config/config.example.yml).
 * Semua nilai operasional ada di sini; tidak ada yang hard-coded —
 * terutama {@code application.path} (AGENTS.md bagian Client Installation
 * Path).
 */
public record AgentConfig(
        Server server,
        Intervals intervals,
        Application application,
        Deployment deployment,
        Backup backup) {

    public record Server(String baseUrl, int timeoutSeconds) {
    }

    public record Intervals(int heartbeatSeconds, int updateCheckSeconds) {
    }

    public record Application(String path, String launcher) {
    }

    public record Deployment(int maxWaitMinutes, int waitPollSeconds) {
    }

    public record Backup(int keep) {
    }

    public static AgentConfig fromMap(Map<String, Object> root) {
        Server server = server(section(root, "server"));
        Intervals intervals = intervals(section(root, "intervals"));
        Application application = application(section(root, "application"));
        Deployment deployment = deployment(section(root, "deployment"));
        Backup backup = backup(section(root, "backup"));
        return new AgentConfig(server, intervals, application, deployment, backup);
    }

    private static Server server(Map<String, Object> s) {
        String baseUrl = str(s, "baseUrl", null);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("server.baseUrl wajib diisi di config");
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return new Server(baseUrl, intVal(s, "timeoutSeconds", 30));
    }

    private static Intervals intervals(Map<String, Object> s) {
        return new Intervals(
                intVal(s, "heartbeatSeconds", 60),
                intVal(s, "updateCheckSeconds", 300));
    }

    private static Application application(Map<String, Object> s) {
        String path = str(s, "path", null);
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("application.path wajib diisi di config (lokasi instalasi SIMRS)");
        }
        return new Application(path, str(s, "launcher", "run.bat"));
    }

    private static Deployment deployment(Map<String, Object> s) {
        return new Deployment(
                intVal(s, "maxWaitMinutes", 120),
                intVal(s, "waitPollSeconds", 30));
    }

    private static Backup backup(Map<String, Object> s) {
        return new Backup(intVal(s, "keep", 1));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> root, String key) {
        Object value = root.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String str(Map<String, Object> s, String key, String fallback) {
        Object value = s.get(key);
        return value != null ? String.valueOf(value) : fallback;
    }

    private static int intVal(Map<String, Object> s, String key, int fallback) {
        Object value = s.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text.trim());
        }
        return fallback;
    }
}
