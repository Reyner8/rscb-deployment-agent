package com.rscb.agent.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void simpanDanMuatUlangMempertahankanSemuaField() throws IOException {
        StateStore store = new StateStore(tempDir.resolve("agent-state.json"));
        AgentState state = new AgentState();
        state.setDeviceId("device-1");
        state.setToken("token-abc");
        state.setCurrentVersion("1.5.0");
        state.setLastDeploymentId("dep-1");
        state.setLastDeploymentResult("SUCCESS");
        state.setLastDeploymentVersion("1.5.0");

        store.save(state);
        AgentState loaded = store.load();

        assertEquals("device-1", loaded.getDeviceId());
        assertEquals("token-abc", loaded.getToken());
        assertEquals("1.5.0", loaded.getCurrentVersion());
        assertEquals("dep-1", loaded.getLastDeploymentId());
        assertEquals("SUCCESS", loaded.getLastDeploymentResult());
        assertEquals("1.5.0", loaded.getLastDeploymentVersion());
        assertTrue(loaded.isRegistered());
    }

    @Test
    void fileYangBelumAdaMenghasilkanStateKosong() throws IOException {
        AgentState state = new StateStore(tempDir.resolve("tidak-ada.json")).load();

        assertNull(state.getDeviceId());
        assertNull(state.getToken());
        assertFalse(state.isRegistered());
    }

    @Test
    void fileCorruptDiarsipkanDanDigantiStateKosong() throws IOException {
        Path file = tempDir.resolve("agent-state.json");
        Files.writeString(file, "{ ini bukan json ]");
        StateStore store = new StateStore(file);

        AgentState state = store.load();

        assertFalse(state.isRegistered());
        assertFalse(Files.exists(file), "state lama dipindah, bukan dibiarkan corrupt");
        try (var files = Files.list(tempDir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().contains(".corrupt-")));
        }
    }

    @Test
    void tulisanAtomikTidakMeninggalkanFileTmp() throws IOException {
        StateStore store = new StateStore(tempDir.resolve("agent-state.json"));
        AgentState state = new AgentState();
        state.setDeviceId("device-1");

        store.save(state);

        assertFalse(Files.exists(tempDir.resolve("agent-state.json.tmp")));
    }
}
