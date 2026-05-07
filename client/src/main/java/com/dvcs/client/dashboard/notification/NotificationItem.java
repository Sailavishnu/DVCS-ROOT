package com.dvcs.client.dashboard.notification;

import java.time.Instant;
import org.bson.types.ObjectId;

public record NotificationItem(
        String type,          // "COLLAB_REQUEST", "COMMIT", "SYSTEM"
        String title,
        String message,
        String workspaceName,
        boolean isRead,
        Instant time,
        ObjectId requestId,   // COLLAB_REQUEST only
        String requestedBy,
        ObjectId workspaceId,
        ObjectId fileId,
        String fileName,
        String commitHash     // COMMIT only
) {
    public static NotificationItem forCollabRequest(
            ObjectId requestId, String requestedBy,
            ObjectId workspaceId, String workspaceName, ObjectId fileId) {
        return new NotificationItem(
                "COLLAB_REQUEST",
                "Collaboration Request",
                requestedBy + " wants to collaborate on " + workspaceName,
                workspaceName, false, Instant.now(),
                requestId, requestedBy, workspaceId, fileId, null, null);
    }

    public static NotificationItem forCommit(
            String fileName, String commitMessage, String committedBy,
            ObjectId workspaceId, String workspaceName, String commitHash, Instant time) {
        return new NotificationItem(
                "COMMIT",
                "New Commit",
                committedBy + " committed: " + commitMessage,
                workspaceName, false,
                time != null ? time : Instant.now(),
                null, committedBy, workspaceId, null, fileName, commitHash);
    }
}
