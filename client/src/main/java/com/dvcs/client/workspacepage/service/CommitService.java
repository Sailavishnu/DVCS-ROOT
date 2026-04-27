package com.dvcs.client.workspacepage.service;

import java.util.Date;
import java.util.Objects;

import org.bson.types.ObjectId;

import com.dvcs.client.workspacepage.dao.FileDAO;

public final class CommitService {

    private final FileDAO fileDAO;

    public CommitService(FileDAO fileDAO) {
        this.fileDAO = Objects.requireNonNull(fileDAO, "fileDAO");
    }

    public int countWorkspaceCommits(ObjectId workspaceId) {
        return fileDAO.countWorkspaceCommits(workspaceId);
    }

    public void createCommit(ObjectId fileId, int snapshotId, String message, ObjectId committedBy, Date committedAt, ObjectId workspaceId) {
        fileDAO.createCommit(fileId, snapshotId, message, committedBy, committedAt, workspaceId);
    }
}
