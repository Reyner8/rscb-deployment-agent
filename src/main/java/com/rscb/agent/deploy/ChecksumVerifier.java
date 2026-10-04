package com.rscb.agent.deploy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Verifikasi artifact: ukuran dan SHA-256 (AGENTS.md bagian Checksum).
 * Artifact yang tidak cocok tidak boleh pernah menyentuh instalasi.
 */
public class ChecksumVerifier {

    public String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 tidak tersedia di JDK ini", e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    /** Melempar IOException bila ukuran atau checksum tidak cocok. */
    public void verify(Path file, String expectedSha256, Long expectedSize) throws IOException {
        if (expectedSize != null) {
            long actual = Files.size(file);
            if (actual != expectedSize) {
                throw new IOException("Ukuran file tidak cocok: harapan " + expectedSize
                        + " byte, aktual " + actual + " byte");
            }
        }
        if (expectedSha256 != null && !expectedSha256.isBlank()) {
            String actual = sha256(file);
            if (!actual.equalsIgnoreCase(expectedSha256.trim())) {
                throw new IOException("SHA-256 tidak cocok: harapan " + expectedSha256
                        + ", aktual " + actual);
            }
        }
    }
}
