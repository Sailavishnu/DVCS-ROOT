package com.dvcs.client.dashboard.notification;

import org.bson.types.ObjectId;

public record NotificationRequestItem(
        ObjectId requestId,
        ObjectId fileId,
        ObjectId workspaceId,
        String workspaceName,
        String requestedByUsername) {
}
