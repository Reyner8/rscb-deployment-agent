package com.rscb.agent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    private Path write(String content) throws IOException {
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, content);
        return file;
    }

    @Test
    void memuatNilaiLengkapDariYaml() throws IOException {
        Path file = write("""
                server:
                  baseUrl: "http://192.168.1.10:3000/"
                  timeoutSeconds: 15
                intervals:
                  heartbeatSeconds: 30
                  updateCheckSeconds: 120
                application:
                  path: "C:\\\\RSCB\\\\SIMRS"
                  launcher: "start.bat"
                deployment:
                  maxWaitMinutes: 60
                  waitPollSeconds: 15
                backup:
                  keep: 2
                """);

        AgentConfig config = ConfigLoader.load(file);

        // Trailing slash dibuang agar resolve path relatif aman.
        assertEquals("http://192.168.1.10:3000", config.server().baseUrl());
        assertEquals(15, config.server().timeoutSeconds());
        assertEquals(30, config.intervals().heartbeatSeconds());
        assertEquals(120, config.intervals().updateCheckSeconds());
        assertEquals("C:\\RSCB\\SIMRS", config.application().path());
        assertEquals("start.bat", config.application().launcher());
        assertEquals(60, config.deployment().maxWaitMinutes());
        assertEquals(15, config.deployment().waitPollSeconds());
        assertEquals(2, config.backup().keep());
    }

    @Test
    void memakaiNilaiDefaultUntukKunciYangTidakAda() throws IOException {
        Path file = write("""
                server:
                  baseUrl: "http://localhost:3000"
                application:
                  path: "/opt/simrs"
                """);

        AgentConfig config = ConfigLoader.load(file);

        assertEquals(30, config.server().timeoutSeconds());
        assertEquals(60, config.intervals().heartbeatSeconds());
        assertEquals(300, config.intervals().updateCheckSeconds());
        assertEquals("run.bat", config.application().launcher());
        assertEquals(120, config.deployment().maxWaitMinutes());
        assertEquals(30, config.deployment().waitPollSeconds());
        assertEquals(1, config.backup().keep());
    }

    @Test
    void menolakConfigTanpaBaseUrl() throws IOException {
        Path file = write("""
                application:
                  path: "C:\\\\SIMRS"
                """);
        IOException e = assertThrows(IOException.class, () -> ConfigLoader.load(file));
        assertEquals(true, e.getMessage().contains("server.baseUrl"));
    }

    @Test
    void menolakConfigTanpaApplicationPath() throws IOException {
        Path file = write("""
                server:
                  baseUrl: "http://localhost:3000"
                """);
        IOException e = assertThrows(IOException.class, () -> ConfigLoader.load(file));
        assertEquals(true, e.getMessage().contains("application.path"));
    }

    @Test
    void menolakFileYangTidakAda() {
        assertThrows(IOException.class, () -> ConfigLoader.load(tempDir.resolve("tidak-ada.yml")));
    }
}
