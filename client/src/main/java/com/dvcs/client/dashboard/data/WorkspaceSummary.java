package com.dvcs.client.dashboard.data;

import java.time.Instant;

import org.bson.types.ObjectId;

public record WorkspaceSummary(
        ObjectId workspaceId,
        String workspaceName,
        String drive,
        String directory,
        String folderName,
        String absolutePath,
        Instant createdAt) {

    public String displayName() {
        if (workspaceName == null || workspaceName.isBlank()) {
            return "Workspace";
        }
        return workspaceName;
    }
}
