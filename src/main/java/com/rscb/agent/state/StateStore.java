package com.rscb.agent.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Memuat dan menyimpan {@link AgentState} sebagai JSON dengan tulisan
 * atomik (tulis ke .tmp lalu rename) supaya proses mati di tengah
 * penulisan tidak meninggalkan state setengah jadi.
 */
public class StateStore {

    private static final System.Logger LOG = System.getLogger(StateStore.class.getName());

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path file;

    public StateStore(Path file) {
        this.file = file;
    }

    public Path getFile() {
        return file;
    }

    public synchronized AgentState load() throws IOException {
        if (!Files.isRegularFile(file)) {
            return new AgentState();
        }
        try {
            return mapper.readValue(Files.readString(file), AgentState.class);
        } catch (IOException e) {
            // State corrupt tidak boleh membuat agent mati permanen: arsipkan
            // lalu mulai dari state kosong (agent akan register ulang).
            Path corrupt = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
            Files.move(file, corrupt, StandardCopyOption.REPLACE_EXISTING);
            LOG.log(System.Logger.Level.WARNING, "State corrupt, diarsipkan ke " + corrupt + " — agent akan register ulang");
            return new AgentState();
        }
    }

    public synchronized void save(AgentState state) throws IOException {
        state.setUpdatedAt(Instant.now().toString());
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, mapper.writeValueAsString(state), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
