package com.dvcs.client.workspacepage.service;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.dvcs.client.workspacepage.dao.FileDAO;
import com.dvcs.client.core.dao.AuditLogDao;
import com.dvcs.client.core.model.AuditLog;
import java.time.Instant;

public final class FileService {

    private final FileDAO fileDAO;
    private final CommitService commitService;
    private final AuditLogDao auditLogDao;

    public FileService(FileDAO fileDAO, CommitService commitService, AuditLogDao auditLogDao) {
        this.fileDAO = Objects.requireNonNull(fileDAO, "fileDAO");
        this.commitService = Objects.requireNonNull(commitService, "commitService");
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao");
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

    public void commit(ObjectId fileId, ObjectId committedBy, String message, String content, ObjectId workspaceId) {
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
        commitService.createCommit(fileId, nextSnapshotId, normalizedMessage, committedBy, now, workspaceId);
        fileDAO.updateFileHead(fileId, nextSnapshotId, normalizedMessage, committedBy, now);

        auditLogDao.insert(new AuditLog(new ObjectId(), committedBy, "COMMIT", "File",
                file.getString("filename"), null, Instant.now()));
    }

    public List<Document> loadFileCommits(ObjectId fileId) {
        Objects.requireNonNull(fileId, "fileId");
        return fileDAO.findCommitsByFileId(fileId);
    }

    public String loadSnapshotContent(ObjectId fileId, int snapshotId) {
        Objects.requireNonNull(fileId, "fileId");
        return fileDAO.loadSnapshotContent(fileId, snapshotId);
    }

    public void restoreSnapshot(ObjectId fileId, int snapshotId, String content) {
        Objects.requireNonNull(fileId, "fileId");

        Document file = fileDAO.findFileById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        // Only update the file content to the restored snapshot, do not create a new
        // commit or snapshot
        int snapshotIdToRestore = snapshotId;
        String filename = file.getString("filename");
        // Optionally, update the file head to point to the restored snapshot
        fileDAO.updateFileHead(fileId, snapshotIdToRestore, "Restored from snapshot #" + snapshotId, null, new Date());

        auditLogDao.insert(new AuditLog(new ObjectId(), null, "RESTORE", "File",
                filename, null, Instant.now()));
    }
}
