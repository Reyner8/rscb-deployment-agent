package com.rscb.agent.update;

import com.rscb.agent.api.Dto;

/**
 * Menerima hasil cek update berkala dari {@code GET /agents/updates}.
 * Implementasi Task 1 hanya mencatat; implementasi deployment (Task 2)
 * akan mengeksekusi alur download → verify → install.
 */
public interface UpdateHandler {

    void handle(Dto.UpdatesResponse updates);
}
