package com.rscb.agent.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryUtilTest {

    @Test
    void suksesTanpaRetryPadaPercobaanPertama() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        String result = RetryUtil.withRetry(() -> {
            calls.incrementAndGet();
            return "ok";
        }, 3, 1, "tes");

        assertEquals("ok", result);
        assertEquals(1, calls.get());
    }

    @Test
    void kegagalanJaringanDiulangSampaiBerhasil() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        String result = RetryUtil.withRetry(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new IOException("koneksi putus");
            }
            return "ok";
        }, 3, 1, "tes");

        assertEquals("ok", result);
        assertEquals(3, calls.get());
    }

    @Test
    void habisPercobaanMelemparErrorTerakhir() {
        AtomicInteger calls = new AtomicInteger();

        IOException e = assertThrows(IOException.class, () -> RetryUtil.withRetry(() -> {
            calls.incrementAndGet();
            throw new IOException("server tidak merespons");
        }, 3, 1, "tes"));

        assertEquals(3, calls.get());
        assertTrue(e.getMessage().contains("server tidak merespons"));
    }

    @Test
    void error4xxTidakDiulang() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(ApiException.class, () -> RetryUtil.withRetry(() -> {
            calls.incrementAndGet();
            throw new ApiException(400, "request tidak valid");
        }, 3, 1, "tes"));

        assertEquals(1, calls.get());
    }

    @Test
    void error5xxTetapDiulang() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(ApiException.class, () -> RetryUtil.withRetry(() -> {
            calls.incrementAndGet();
            throw new ApiException(503, "server sibuk");
        }, 3, 1, "tes"));

        assertEquals(3, calls.get());
    }
}
