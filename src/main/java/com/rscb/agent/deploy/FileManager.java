package com.rscb.agent.deploy;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Strategi penggantian file (design-patterns.md §25–§26):
 *
 * <pre>
 * current --backup--> .deployment/backup/&lt;timestamp&gt;/
 * staged (.deployment/staging/&lt;version&gt;) --replace--> current
 * gagal --> restore dari backup
 * </pre>
 *
 * Directory internal berada di {@code <application.path>/.deployment} dan
 * dipindahkan. Instalasi bertahap; kegagalan tidak boleh meninggalkan
 * instalasi rusak tanpa jalan kembali.
 */
public class FileManager {

    public static final String INTERNAL_DIR = ".deployment";

    private final Path applicationPath;

    public FileManager(Path applicationPath) {
        this.applicationPath = applicationPath;
    }

    public Path internalDir() {
        return applicationPath.resolve(INTERNAL_DIR);
    }

    public Path downloadsDir() throws IOException {
        return ensureDir(internalDir().resolve("downloads"));
    }

    public Path stagingDir() throws IOException {
        return ensureDir(internalDir().resolve("staging"));
    }

    public Path backupDir() throws IOException {
        return ensureDir(internalDir().resolve("backup"));
    }

    /**
     * Mengekstrak ZIP ke {@code .deployment/staging/<version>/} dengan guard
     * zip-slip: entry yang keluar dari directory staging ditolak. Mengembalikan
     * directory staging untuk versi tersebut.
     */
    public Path unzip(Path zip, String version) throws IOException {
        Path stage = stagingDir().resolve(version);
        deleteRecursively(stage);
        Files.createDirectories(stage);
        Path canonicalStage = stage.toAbsolutePath().normalize();
        boolean anyEntry = false;
        try (ZipInputStream zipStream = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                anyEntry = true;
                Path target = canonicalStage.resolve(entry.getName()).normalize();
                if (!target.startsWith(canonicalStage)) {
                    throw new IOException("Zip slip terdeteksi pada entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zipStream, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zipStream.closeEntry();
            }
        }
        if (!anyEntry) {
            // ZIP kosong/rusak tidak boleh sampai dipasang — instalasi bisa
            // tertukar dengan directory kosong.
            throw new IOException("Artifact ZIP kosong atau tidak valid");
        }
        return stage;
    }

    /**
     * Memindahkan isi instalasi saat ini (kecuali directory internal
     * {@code .deployment}) ke directory backup, mengembalikan path backup
     * atau null bila instalasi masih kosong.
     */
    public Path backupCurrent() throws IOException {
        Path backup = backupDir().resolve("backup-" + System.currentTimeMillis());
        Files.createDirectories(backup);
        boolean any = false;
        for (Path entry : listTopLevel(applicationPath)) {
            if (entry.getFileName().toString().equals(INTERNAL_DIR)) {
                continue;
            }
            move(entry, backup.resolve(entry.getFileName().toString()));
            any = true;
        }
        if (!any) {
            Files.deleteIfExists(backup);
            return null;
        }
        return backup;
    }

    /**
     * Memindahkan isi directory staging ke instalasi utama. Mengembalikan
     * daftar nama entry tingkat atas yang dipindahkan (untuk restore saat
     * gagal).
     */
    public List<String> installStaged(Path stagingVersionDir) throws IOException {
        List<String> installed = new ArrayList<>();
        for (Path entry : listTopLevel(stagingVersionDir)) {
            move(entry, applicationPath.resolve(entry.getFileName().toString()));
            installed.add(entry.getFileName().toString());
        }
        return installed;
    }

    /** Mengembalikan isi backup ke instalasi: hapus sisa instalasi gagal lalu
     * pindahkan kembali isi backup. */
    public void restoreBackup(Path backup, List<String> stagedTopLevelNames) throws IOException {
        for (String name : stagedTopLevelNames) {
            deleteRecursively(applicationPath.resolve(name));
        }
        for (Path entry : listTopLevel(backup)) {
            move(entry, applicationPath.resolve(entry.getFileName().toString()));
        }
    }

    /** Menghapus backup lama, menyisakan {@code keep} backup terbaru. */
    public void pruneBackups(int keep) throws IOException {
        Path backupRoot = backupDir();
        List<Path> backups;
        try (Stream<Path> stream = Files.list(backupRoot)) {
            backups = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
        }
        for (int i = keep; i < backups.size(); i++) {
            deleteRecursively(backups.get(i));
        }
    }

    /** Menghapus file download yang tidak tersentuh lebih dari 24 jam. */
    public void cleanupDownloads() throws IOException {
        Instant threshold = Instant.now().minusSeconds(24 * 3600);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(downloadsDir())) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)
                        && Files.getLastModifiedTime(file).toInstant().isBefore(threshold)) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    // ------------------------------------------------------------------

    private Path ensureDir(Path dir) throws IOException {
        Files.createDirectories(dir);
        return dir;
    }

    private List<Path> listTopLevel(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            List<Path> entries = new ArrayList<>();
            stream.forEach(entries::add);
            return entries;
        }
    }

    private void move(Path from, Path to) throws IOException {
        Files.createDirectories(to.toAbsolutePath().getParent());
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // beda volume / file terkunci: salin lalu hapus sumber
            copyRecursively(from, to);
            deleteRecursively(from);
        }
    }

    private void copyRecursively(Path from, Path to) throws IOException {
        if (Files.isDirectory(from)) {
            Files.createDirectories(to);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(from)) {
                for (Path child : stream) {
                    copyRecursively(child, to.resolve(child.getFileName().toString()));
                }
            }
        } else {
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.delete(path);
    }
}
