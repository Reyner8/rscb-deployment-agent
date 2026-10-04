package com.rscb.agent.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionsTest {

    @Test
    void membandingkanSecaraNumerikPerSegmen() {
        assertEquals(1, Versions.compare("1.5.0", "1.4.9"));
        assertEquals(-1, Versions.compare("1.4.9", "1.5.0"));
        assertEquals(0, Versions.compare("1.5.0", "1.5.0"));
        // 10 > 2 secara numerik, bukan leksikografis
        assertEquals(1, Versions.compare("1.5.10", "1.5.2"));
        assertEquals(1, Versions.compare("2.0.0", "1.9.9"));
    }

    @Test
    void segmenYangHilangDianggapNol() {
        assertEquals(0, Versions.compare("1.5", "1.5.0"));
        assertEquals(1, Versions.compare("1.5.1", "1.5"));
    }

    @Test
    void segmenNonNumerikMelemparNumberFormatException() {
        assertThrows(NumberFormatException.class, () -> Versions.compare("1.0.0-beta", "1.0.0"));
    }

    @Test
    void isNewerMenggunakanFallbackKesetaraanSaatParseGagal() {
        assertTrue(Versions.isNewer("1.6.0", "1.5.0"));
        assertFalse(Versions.isNewer("1.5.0", "1.6.0"));
        assertFalse(Versions.isNewer("1.5.0", "1.5.0"));
        // fallback (sama seperti backend): tidak sama dianggap lebih baru
        assertTrue(Versions.isNewer("1.0.0-rc1", "1.5.0"));
        assertTrue(Versions.isNewer("1.5.0", "1.5.0-rc1"));
    }
}
