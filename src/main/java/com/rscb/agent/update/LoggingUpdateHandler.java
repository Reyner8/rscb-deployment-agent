package com.rscb.agent.update;

import com.rscb.agent.api.Dto;

import java.lang.System.Logger.Level;

/** Implementasi {@link UpdateHandler} yang hanya mencatat hasil cek update. */
public class LoggingUpdateHandler implements UpdateHandler {

    private static final System.Logger LOG = System.getLogger(LoggingUpdateHandler.class.getName());

    @Override
    public void handle(Dto.UpdatesResponse updates) {
        if (!updates.hasUpdate()) {
            LOG.log(Level.DEBUG, "Tidak ada update (latest={0})", updates.latestVersion());
            return;
        }
        LOG.log(Level.INFO, "Update tersedia: {0} (deployment={1}, status={2})",
                updates.latestVersion(), updates.deploymentId(), updates.deploymentStatus());
    }
}
