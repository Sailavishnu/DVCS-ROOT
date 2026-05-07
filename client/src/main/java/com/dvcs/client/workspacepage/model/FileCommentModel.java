package com.dvcs.client.workspacepage.model;

import java.time.Instant;
import org.bson.types.ObjectId;

public record FileCommentModel(
        ObjectId commentId,
        ObjectId workspaceId,
        ObjectId fileId,
        ObjectId authorId,
        String message,
        Instant createdAt) {
}
