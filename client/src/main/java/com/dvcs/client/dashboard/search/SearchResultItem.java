package com.dvcs.client.dashboard.search;

import org.bson.types.ObjectId;

public record SearchResultItem(
        ObjectId workspaceId,
        ObjectId folderId,
        ObjectId fileId,
        String workspaceName,
        String folderName,
        String fileName,
        String relativePath) {
}
