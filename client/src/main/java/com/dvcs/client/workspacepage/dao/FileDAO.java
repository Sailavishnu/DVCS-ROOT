package com.dvcs.client.workspacepage.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import com.mongodb.client.model.Updates;

public final class FileDAO {

    private final MongoCollection<Document> files;
    private final MongoCollection<Document> folders;
    private final MongoCollection<Document> snapshots;
    private final MongoCollection<Document> commits;

    public FileDAO(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.files = database.getCollection("files");
        this.folders = database.getCollection("folders");
        this.snapshots = database.getCollection("file_snapshots");
        this.commits = database.getCollection("commits");
    }

    public Optional<Document> findFileById(ObjectId fileId) {
        return Optional.ofNullable(files.find(eq("_id", fileId)).first());
    }

    public Optional<Document> findFileByFolderIdAndName(ObjectId folderId, String filename) {
        return Optional.ofNullable(files.find(and(eq("folderId", folderId), eq("filename", filename))).first());
    }

    public ObjectId insertFile(Document fileDoc) {
        files.insertOne(fileDoc);
        return fileDoc.getObjectId("_id");
    }

    public Optional<Document> findRootReadme(ObjectId workspaceId) {
        Document rootFolder = folders.find(and(eq("workspaceId", workspaceId), eq("folderName", "root"))).first();
        if (rootFolder == null) {
            return Optional.empty();
        }
        ObjectId folderId = rootFolder.getObjectId("_id");
        if (folderId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(files.find(and(eq("folderId", folderId), eq("filename", "README.md"))).first());
    }

    public String loadSnapshotContent(ObjectId fileId, int snapshotId) {
        Document snapshot = snapshots.find(and(eq("fileId", fileId), eq("snapshotId", snapshotId))).first();
        if (snapshot == null) {
            return "";
        }
        String content = snapshot.getString("content");
        return content == null ? "" : content;
    }

    public String loadLatestContent(ObjectId fileId) {
        Document latest = snapshots.find(eq("fileId", fileId))
                .sort(new Document("snapshotId", -1))
                .first();
        if (latest == null) {
            return "";
        }
        String content = latest.getString("content");
        return content == null ? "" : content;
    }

    public void createSnapshot(ObjectId fileId, int snapshotId, String content, Date createdAt) {
        snapshots.insertOne(new Document("_id", new ObjectId())
                .append("fileId", fileId)
                .append("snapshotId", snapshotId)
                .append("content", content)
                .append("createdAt", createdAt));
    }

    public void createCommit(ObjectId fileId, int snapshotId, String message, ObjectId committedBy, Date committedAt) {
        commits.insertOne(new Document("_id", new ObjectId())
                .append("fileId", fileId)
                .append("snapshotId", snapshotId)
                .append("message", message)
                .append("committedBy", committedBy)
                .append("committedAt", committedAt));
    }

    public void updateFileHead(ObjectId fileId, int snapshotId, String message, ObjectId committedBy,
            Date committedAt) {
        Document latestCommit = new Document("message", message)
                .append("committedBy", committedBy)
                .append("committedAt", committedAt);

        files.updateOne(eq("_id", fileId), Updates.combine(
                Updates.set("latestCommit", latestCommit),
                Updates.set("currentSnapshotId", snapshotId),
                Updates.set("lockStatus.isLocked", false),
                Updates.set("lockStatus.lockedBy", null),
                Updates.set("lockStatus.lockedAt", null)));
    }

    public int countWorkspaceCommits(ObjectId workspaceId) {
        List<ObjectId> folderIds = folders.find(eq("workspaceId", workspaceId))
                .map(doc -> doc.getObjectId("_id"))
                .into(new ArrayList<>());
        if (folderIds.isEmpty()) {
            return 0;
        }

        List<ObjectId> fileIds = files.find(in("folderId", folderIds))
                .map(doc -> doc.getObjectId("_id"))
                .into(new ArrayList<>());
        if (fileIds.isEmpty()) {
            return 0;
        }

        return (int) commits.countDocuments(in("fileId", fileIds));
    }

    public void deleteWorkspaceRecords(List<ObjectId> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        snapshots.deleteMany(in("fileId", fileIds));
        commits.deleteMany(in("fileId", fileIds));
    }

    public List<Document> findCommitsByFileId(ObjectId fileId) {
        return commits.find(eq("fileId", fileId))
                .sort(new Document("snapshotId", -1))
                .into(new ArrayList<>());
    }

    public void renameFile(ObjectId fileId, String filename, String extension, String relativePath) {
        files.updateOne(eq("_id", fileId), Updates.combine(
                Updates.set("filename", filename),
                Updates.set("extension", extension),
                Updates.set("path.relativePath", relativePath),
                Updates.set("path.versionRoot", ".versions/" + filename)));
    }

    public void deleteFile(ObjectId fileId) {
        snapshots.deleteMany(eq("fileId", fileId));
        commits.deleteMany(eq("fileId", fileId));
        files.deleteOne(eq("_id", fileId));
    }

    public List<Document> findCommitsByWorkspace(ObjectId workspaceId) {
        List<ObjectId> folderIds = folders.find(eq("workspaceId", workspaceId))
                .map(doc -> doc.getObjectId("_id"))
                .into(new ArrayList<>());
        if (folderIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<ObjectId> fileIds = files.find(in("folderId", folderIds))
                .map(doc -> doc.getObjectId("_id"))
                .into(new ArrayList<>());
        if (fileIds.isEmpty()) {
            return new ArrayList<>();
        }

        return commits.find(in("fileId", fileIds))
                .sort(new Document("committedAt", -1))
                .into(new ArrayList<>());
    }

    public List<Document> findSnapshotsByFileId(ObjectId fileId) {
        return snapshots.find(eq("fileId", fileId))
                .sort(new Document("snapshotId", 1))
                .into(new ArrayList<>());
    }

    public List<Document> findFilesByWorkspace(ObjectId workspaceId) {
        List<ObjectId> folderIds = folders.find(eq("workspaceId", workspaceId))
                .map(doc -> doc.getObjectId("_id"))
                .into(new ArrayList<>());
        if (folderIds.isEmpty()) {
            return new ArrayList<>();
        }

        return files.find(in("folderId", folderIds))
                .sort(new Document("filename", 1))
                .into(new ArrayList<>());
    }
}
