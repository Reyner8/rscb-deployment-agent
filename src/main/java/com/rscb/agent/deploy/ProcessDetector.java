package com.rscb.agent.deploy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Mendeteksi apakah aplikasi SIMRS sedang berjalan (design-patterns.md §24).
 * Agent TIDAK PERNAH memaksa stop — prosesnya harus ditutup user; agent
 * hanya menunggu (status WAITING di server).
 */
public interface ProcessDetector {

    /** True bila ada proses yang command line-nya mengandung path aplikasi. */
    boolean isRunning(Path applicationPath) throws IOException;

    static ProcessDetector create() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return new WindowsProcessDetector();
        }
        return new UnixProcessDetector();
    }
}

/** Deteksi via PowerShell/CIM: cocokkan command line proses dengan path. */
class WindowsProcessDetector implements ProcessDetector {

    @Override
    public boolean isRunning(Path applicationPath) throws IOException {
        String normalized = applicationPath.toString().trim();
        while (normalized.endsWith("\\")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String escaped = normalized.replace("'", "''");
        String command =
                "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -ne $null "
                        + "-and $_.CommandLine.ToLower().Contains('" + escaped.toLowerCase(Locale.ROOT) + "') } "
                        + "| Select-Object -First 1 -ExpandProperty ProcessId";
        ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-Command", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try {
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Deteksi proses (PowerShell) timeout");
            }
            String output = new String(process.getInputStream().readAllBytes()).trim();
            return process.exitValue() == 0 && !output.isEmpty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Deteksi proses terputus", e);
        }
    }
}

/** Deteksi via pgrep -f untuk pengembangan/testing di Unix. */
class UnixProcessDetector implements ProcessDetector {

    @Override
    public boolean isRunning(Path applicationPath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "pgrep", "-f", "--", applicationPath.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try {
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Deteksi proses (pgrep) timeout");
            }
            return process.exitValue() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Deteksi proses terputus", e);
        }
    }
}
