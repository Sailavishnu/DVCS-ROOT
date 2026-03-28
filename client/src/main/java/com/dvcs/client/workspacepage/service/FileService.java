package com.dvcs.client.workspacepage.service;

import java.util.Date;
import java.util.Objects;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.dvcs.client.workspacepage.dao.FileDAO;

public final class FileService {

    private final FileDAO fileDAO;
    private final CommitService commitService;

    public FileService(FileDAO fileDAO, CommitService commitService) {
        this.fileDAO = Objects.requireNonNull(fileDAO, "fileDAO");
        this.commitService = Objects.requireNonNull(commitService, "commitService");
    }

    public String loadContent(ObjectId fileId) {
        Document file = fileDAO.findFileById(fileId).orElse(null);
        if (file == null) {
            return "";
        }

        Integer snapshotId = file.getInteger("currentSnapshotId", 0);
        if (snapshotId != null && snapshotId > 0) {
            return fileDAO.loadSnapshotContent(fileId, snapshotId);
        }
        return fileDAO.loadLatestContent(fileId);
    }

    public void commit(ObjectId fileId, ObjectId committedBy, String message, String content) {
        Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(committedBy, "committedBy");

        String normalizedMessage = message == null ? "" : message.trim();
        if (normalizedMessage.isEmpty()) {
            throw new IllegalArgumentException("Commit message is required");
        }

        Document file = fileDAO.findFileById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        int currentSnapshotId = file.getInteger("currentSnapshotId", 0);
        int nextSnapshotId = currentSnapshotId + 1;
        Date now = new Date();

        fileDAO.createSnapshot(fileId, nextSnapshotId, content == null ? "" : content, now);
        commitService.createCommit(fileId, nextSnapshotId, normalizedMessage, committedBy, now);
        fileDAO.updateFileHead(fileId, nextSnapshotId, normalizedMessage, committedBy, now);
    }
}
