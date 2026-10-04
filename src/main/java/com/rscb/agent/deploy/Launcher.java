package com.rscb.agent.deploy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Menjalankan kembali aplikasi SIMRS setelah instalasi selesai. */
public interface Launcher {

    void start(Path applicationPath, String launcher) throws IOException;

    /** Menjalankan run.bat lewat cmd start agar proses SIMRS terpisah (detached)
     * dari proses agent — agent tidak boleh menjadi parent yang menahan
     * lifetime SIMRS. */
    DefaultLauncher DEFAULT = new DefaultLauncher();

    class DefaultLauncher implements Launcher {

        @Override
        public void start(Path applicationPath, String launcher) throws IOException {
            boolean windows = System.getProperty("os.name", "")
                    .toLowerCase(Locale.ROOT).contains("win");
            ProcessBuilder pb = windows
                    ? new ProcessBuilder("cmd", "/c", "start", "", launcher)
                    : new ProcessBuilder("sh", "-c", "./" + launcher);
            pb.directory(applicationPath.toFile());
            pb.inheritIO();
            pb.start(); // tidak ditunggu: agent kembali ke loop utama
        }
    }
}
