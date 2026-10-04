package org.blr.workspace;

public interface WorkspaceManager {

    WorkspaceContext prepareWorkspace(String buildId);

    void cleanupWorkspace(WorkspaceContext context);
}
