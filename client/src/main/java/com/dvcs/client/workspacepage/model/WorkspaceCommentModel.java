package com.dvcs.client.workspacepage.model;

import java.time.Instant;
import org.bson.types.ObjectId;

public record WorkspaceCommentModel(
        ObjectId commentId,
        ObjectId workspaceId,
        ObjectId authorId,
        String message,
        Instant createdAt) {
}
