package com.dvcs.client.dashboard.data;

import org.bson.types.ObjectId;

public record PendingRequestView(
        ObjectId requestId,
        ObjectId fileId,
        String fileName,
        String requestedByUsername) {
}
