package com.dvcs.client.workspacepage.model;

import java.time.Instant;

import org.bson.types.ObjectId;

public record FileItemModel(
        ObjectId fileId,
        ObjectId folderId,
        String folderName,
        String filename,
        String latestCommitMessage,
        Instant latestCommitAt,
        int currentSnapshotId,
        boolean locked,
        ObjectId lockedBy) {
}
