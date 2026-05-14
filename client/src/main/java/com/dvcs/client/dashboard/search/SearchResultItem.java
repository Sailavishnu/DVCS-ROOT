package com.dvcs.client.dashboard.search;

import java.time.Instant;
import org.bson.types.ObjectId;

public record SearchResultItem(
        ObjectId workspaceId,
        ObjectId workspaceOwnerId,
        ObjectId folderId,
        ObjectId fileId,
        String workspaceName,
        String folderName,
        String fileName,
        String relativePath,
        String type,            // "FILE", "COMMIT", "WORKSPACE"
        String contentPreview,  // latest commit message for files; commit message for commits
        String commitHash,      // short hash string for COMMIT type
        String committedBy,     // username of last committer
        Instant lastModifiedAt
) {
    public static SearchResultItem file(ObjectId workspaceId, ObjectId folderId, ObjectId fileId,
            String workspaceName, String folderName, String fileName, String relativePath,
            String contentPreview, String committedBy, Instant lastModifiedAt) {
        return new SearchResultItem(workspaceId, null, folderId, fileId, workspaceName, folderName,
                fileName, relativePath, "FILE", contentPreview, null, committedBy, lastModifiedAt);
    }

    public static SearchResultItem commit(ObjectId workspaceId, ObjectId folderId, ObjectId fileId,
            String workspaceName, String folderName, String fileName, String relativePath,
            String commitMessage, String commitHash, String committedBy, Instant committedAt) {
        return new SearchResultItem(workspaceId, null, folderId, fileId, workspaceName, folderName,
                fileName, relativePath, "COMMIT", commitMessage, commitHash, committedBy, committedAt);
    }

    public static SearchResultItem workspace(ObjectId workspaceId, ObjectId ownerId,
            String workspaceName, String contentPreview, Instant createdAt) {
        return new SearchResultItem(workspaceId, ownerId, null, null, workspaceName, null,
                workspaceName, workspaceName, "WORKSPACE", contentPreview, null, null, createdAt);
    }
}
