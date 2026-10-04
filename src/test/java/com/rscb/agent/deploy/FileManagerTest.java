package com.rscb.agent.deploy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileManagerTest {

    @TempDir
    Path root;

    private Path appPath;
    private FileManager files;

    private Path newZip(String... entries) throws IOException {
        Path zip = root.resolve("artifact.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (int i = 0; i < entries.length; i += 2) {
                out.putNextEntry(new ZipEntry(entries[i]));
                out.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return zip;
    }

    private void setupApp() throws IOException {
        appPath = root.resolve("app");
        Files.createDirectories(appPath);
        Files.writeString(appPath.resolve("application.jar"), "versi-lama");
        files = new FileManager(appPath);
    }

    @Test
    void unzipMengekstrakKeStagingDenganNamaVersi() throws IOException {
        setupApp();
        Path zip = newZip("run.bat", "@echo off", "lib/app.jar", "JAR");

        Path staged = files.unzip(zip, "1.5.0");

        assertEquals("@echo off", Files.readString(staged.resolve("run.bat")));
        assertEquals("JAR", Files.readString(staged.resolve("lib/app.jar")));
        assertTrue(staged.toString().contains(".deployment" + Paths.get("").getFileSystem().getSeparator() + "staging"));
    }

    @Test
    void unzipMenolakZipKosong() throws IOException {
        setupApp();
        Path zip = root.resolve("empty.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            // tanpa entry — mensimulasikan artifact rusak
        }

        IOException e = assertThrows(IOException.class, () -> files.unzip(zip, "1.0.0"));
        assertTrue(e.getMessage().contains("kosong"));
    }

    @Test
    void unzipMenolakZipSlip() throws IOException {
        setupApp();
        Path zip = root.resolve("evil.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("../evil.txt"));
            out.write("payload".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        IOException e = assertThrows(IOException.class, () -> files.unzip(zip, "1.0.0"));
        assertTrue(e.getMessage().contains("Zip slip"));
        assertFalse(Files.exists(root.resolve("evil.txt")));
    }

    @Test
    void backupMemindahkanIsiInstalasiKecualiDirectoryInternal() throws IOException {
        setupApp();
        Files.writeString(appPath.resolve("config.ini"), "x");

        Path backup = files.backupCurrent();

        assertTrue(Files.exists(backup.resolve("application.jar")));
        assertTrue(Files.exists(backup.resolve("config.ini")));
        assertFalse(Files.exists(appPath.resolve("application.jar")));
        assertTrue(Files.isDirectory(appPath.resolve(".deployment")), "directory internal tetap di tempat");
        assertEquals("versi-lama", Files.readString(backup.resolve("application.jar")));
    }

    @Test
    void backupInstalasiKosongMengembalikanNull() throws IOException {
        setupApp();
        Files.delete(appPath.resolve("application.jar"));

        assertNull(files.backupCurrent());
    }

    @Test
    void installStagedMemindahkanIsiStagingKeInstalasiUtama() throws IOException {
        setupApp();
        Path zip = newZip("application.jar", "versi-baru", "run.bat", "start");
        Path staged = files.unzip(zip, "2.0.0");

        List<String> installed = files.installStaged(staged);

        assertTrue(installed.containsAll(List.of("application.jar", "run.bat")));
        assertEquals("versi-baru", Files.readString(appPath.resolve("application.jar")));
        assertTrue(Files.exists(appPath.resolve("run.bat")));
    }

    @Test
    void restoreBackupMengembalikanInstalasiLamaSetelahInstalasiGagal() throws IOException {
        setupApp();
        Path zip = newZip("application.jar", "versi-rusak");
        Path staged = files.unzip(zip, "3.0.0");
        Path backup = files.backupCurrent();
        List<String> installed = files.installStaged(staged);
        assertEquals("versi-rusak", Files.readString(appPath.resolve("application.jar")));

        files.restoreBackup(backup, installed);

        assertEquals("versi-lama", Files.readString(appPath.resolve("application.jar")));
        assertFalse(Files.exists(backup.resolve("application.jar")), "isi backup dipindah balik");
    }

    @Test
    void pruneBackupsMenyisakanBackupTerbaruSesuaiKeep() throws IOException {
        setupApp();
        Path backupRoot = files.backupDir();
        for (String name : new String[] {"backup-1", "backup-2", "backup-3"}) {
            Path dir = backupRoot.resolve(name);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("marker"), name);
        }

        files.pruneBackups(1);

        assertTrue(Files.exists(backupRoot.resolve("backup-3")), "backup terbaru dipertahankan");
        assertFalse(Files.exists(backupRoot.resolve("backup-1")));
        assertFalse(Files.exists(backupRoot.resolve("backup-2")));
    }

    @Test
    void cleanupDownloadsMenghapusFileLebihTuaDari24Jam() throws IOException {
        setupApp();
        Path old = files.downloadsDir().resolve("old.zip");
        Path fresh = files.downloadsDir().resolve("fresh.zip");
        Files.writeString(old, "x");
        Files.writeString(fresh, "x");
        Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.fromMillis(
                System.currentTimeMillis() - 25 * 3600 * 1000L));

        files.cleanupDownloads();

        assertFalse(Files.exists(old));
        assertTrue(Files.exists(fresh));
    }
}
