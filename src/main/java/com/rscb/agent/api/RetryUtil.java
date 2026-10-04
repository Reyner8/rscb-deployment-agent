package com.rscb.agent.api;

import java.util.concurrent.Callable;

/**
 * Retry terbatas dengan backoff eksponensial untuk operasi jaringan
 * (design-patterns.md §28). Error HTTP 4xx tidak pernah diulang — server
 * sudah menjawab pasti; yang diulang hanya kegagalan koneksi dan 5xx.
 */
public final class RetryUtil {

    private static final System.Logger LOG = System.getLogger(RetryUtil.class.getName());

    private RetryUtil() {
    }

    public static <T> T withRetry(Callable<T> action, int attempts, long initialBackoffMs,
                                  String label) throws Exception {
        long backoff = initialBackoffMs;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return action.call();
            } catch (Exception e) {
                if (attempt == attempts || isClientError(e)) {
                    throw e;
                }
                LOG.log(System.Logger.Level.WARNING,
                        "{0} gagal (percobaan {1}/{2}), dicoba lagi dalam {3} ms: {4}",
                        label, attempt, attempts, backoff, e.getMessage());
                Thread.sleep(backoff);
                backoff *= 2;
            }
        }
        throw new IllegalStateException("Tidak terjangkau");
    }

    private static boolean isClientError(Exception e) {
        return e instanceof ApiException apiException
                && apiException.getStatusCode() >= 400
                && apiException.getStatusCode() < 500;
    }
}
