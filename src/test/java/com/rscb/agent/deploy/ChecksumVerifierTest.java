package com.rscb.agent.deploy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecksumVerifierTest {

    @TempDir
    Path tempDir;

    private final ChecksumVerifier verifier = new ChecksumVerifier();

    @Test
    void verifikasiLolosUntukUkuranDanChecksumYangCocok() throws IOException {
        Path file = tempDir.resolve("a.zip");
        byte[] payload = "isi artifact simulrs".getBytes(StandardCharsets.UTF_8);
        Files.write(file, payload);
        String sha = verifier.sha256(file);

        assertDoesNotThrow(() -> verifier.verify(file, sha, (long) payload.length));
    }

    @Test
    void verifikasiLolosTanpaMetadata() throws IOException {
        Path file = tempDir.resolve("a.zip");
        Files.write(file, new byte[] {1});

        assertDoesNotThrow(() -> verifier.verify(file, null, null));
        assertDoesNotThrow(() -> verifier.verify(file, "", null));
    }

    @Test
    void menolakUkuranYangTidakCocok() throws IOException {
        Path file = tempDir.resolve("a.zip");
        Files.write(file, new byte[] {1, 2, 3});

        IOException e = assertThrows(IOException.class,
                () -> verifier.verify(file, null, 100L));
        assertTrue(e.getMessage().contains("Ukuran file tidak cocok"));
    }

    @Test
    void menolakChecksumYangTidakCocok() throws IOException {
        Path file = tempDir.resolve("a.zip");
        Files.write(file, "data".getBytes(StandardCharsets.UTF_8));

        IOException e = assertThrows(IOException.class,
                () -> verifier.verify(file, "0".repeat(64), null));
        assertTrue(e.getMessage().contains("SHA-256 tidak cocok"));
    }

    @Test
    void checksumKonsistenDenganFormatHex64Karakter() throws IOException {
        Path file = tempDir.resolve("a.zip");
        Files.write(file, "RSCB".getBytes(StandardCharsets.UTF_8));

        String sha = verifier.sha256(file);

        assertEquals(64, sha.length());
        assertEquals(sha.toLowerCase(), sha);
    }
}
