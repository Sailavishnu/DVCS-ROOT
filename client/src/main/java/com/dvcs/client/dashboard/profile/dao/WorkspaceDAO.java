package com.dvcs.client.dashboard.profile.dao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.nio.charset.StandardCharsets;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.dvcs.client.dashboard.profile.ProfileService;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public final class WorkspaceDAO {

    private final MongoCollection<Document> workspaces;
    private final MongoCollection<Document> folders;
    private final MongoCollection<Document> files;
    private final MongoCollection<Document> snapshots;

    public WorkspaceDAO(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.workspaces = database.getCollection("workspaces");
        this.folders = database.getCollection("folders");
        this.files = database.getCollection("files");
        this.snapshots = database.getCollection("file_snapshots");
    }

    public List<ObjectId> findWorkspaceIdsByCreator(ObjectId userId) {
        Objects.requireNonNull(userId, "userId");
        List<Document> docs = workspaces.find(new Document("createdBy", userId)).into(new ArrayList<>());
        List<ObjectId> ids = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            ObjectId id = doc.getObjectId("_id");
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    public int countFoldersByWorkspaceIds(List<ObjectId> workspaceIds) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return 0;
        }
        return (int) folders.countDocuments(new Document("workspaceId", new Document("$in", workspaceIds)));
    }

    public int countFilesByWorkspaceIds(List<ObjectId> workspaceIds) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return 0;
        }

        List<Document> folderDocs = folders.find(new Document("workspaceId", new Document("$in", workspaceIds)))
                .into(new ArrayList<>());
        List<ObjectId> folderIds = new ArrayList<>(folderDocs.size());
        for (Document folderDoc : folderDocs) {
            ObjectId id = folderDoc.getObjectId("_id");
            if (id != null) {
                folderIds.add(id);
            }
        }

        if (folderIds.isEmpty()) {
            return 0;
        }
        return (int) files.countDocuments(new Document("folderId", new Document("$in", folderIds)));
    }

    public Map<ObjectId, String> findWorkspaceNamesByIds(List<ObjectId> workspaceIds) {
        Map<ObjectId, String> names = new LinkedHashMap<>();
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return names;
        }

        List<Document> docs = workspaces.find(new Document("_id", new Document("$in", workspaceIds)))
                .into(new ArrayList<>());
        for (Document doc : docs) {
            ObjectId id = doc.getObjectId("_id");
            String name = doc.getString("workspaceName");
            if (id != null) {
                names.put(id, name == null || name.isBlank() ? "Workspace" : name);
            }
        }
        return names;
    }

    public Map<LocalDate, Integer> loadLast30DayWorkspaceCreationCounts(ObjectId userId) {
        Objects.requireNonNull(userId, "userId");
        return countDocumentsByDay(workspaces, userId);
    }

    public Map<LocalDate, Integer> loadLast30DayFolderCreationCounts(ObjectId userId) {
        Objects.requireNonNull(userId, "userId");
        return countDocumentsByDay(folders, userId);
    }

    public Map<LocalDate, Integer> loadLast30DayFileCreationCounts(ObjectId userId) {
        Objects.requireNonNull(userId, "userId");
        return countDocumentsByDay(files, userId);
    }

    public long loadEstimatedStorageBytes(List<ObjectId> workspaceIds) {
        long total = 0L;
        for (ProfileService.FileSizeEntry file : loadLargestFilesInternal(workspaceIds, Integer.MAX_VALUE)) {
            total += Math.max(0L, file.sizeBytes());
        }
        return total;
    }

    public List<ProfileService.FileSizeEntry> loadLargestFiles(List<ObjectId> workspaceIds, int limit) {
        List<ProfileService.FileSizeEntry> entries = loadLargestFilesInternal(workspaceIds, limit);
        if (entries.size() <= limit) {
            return entries;
        }
        return new ArrayList<>(entries.subList(0, limit));
    }

    private List<ProfileService.FileSizeEntry> loadLargestFilesInternal(List<ObjectId> workspaceIds, int limit) {
        List<ProfileService.FileSizeEntry> result = new ArrayList<>();
        if (workspaceIds == null || workspaceIds.isEmpty() || limit <= 0) {
            return result;
        }

        List<Document> folderDocs = folders.find(new Document("workspaceId", new Document("$in", workspaceIds)))
                .into(new ArrayList<>());
        if (folderDocs.isEmpty()) {
            return result;
        }

        Map<ObjectId, ObjectId> workspaceIdByFolderId = new HashMap<>();
        List<ObjectId> folderIds = new ArrayList<>(folderDocs.size());
        for (Document folderDoc : folderDocs) {
            ObjectId folderId = folderDoc.getObjectId("_id");
            ObjectId workspaceId = folderDoc.getObjectId("workspaceId");
            if (folderId != null) {
                folderIds.add(folderId);
                workspaceIdByFolderId.put(folderId, workspaceId);
            }
        }
        if (folderIds.isEmpty()) {
            return result;
        }

        List<Document> fileDocs = files.find(new Document("folderId", new Document("$in", folderIds)))
                .into(new ArrayList<>());
        if (fileDocs.isEmpty()) {
            return result;
        }

        Map<ObjectId, String> workspaceNames = findWorkspaceNamesByIds(workspaceIds);
        Map<ObjectId, Integer> targetSnapshotByFileId = new HashMap<>();
        Map<ObjectId, Document> fileDocById = new HashMap<>();
        List<ObjectId> fileIds = new ArrayList<>(fileDocs.size());
        for (Document fileDoc : fileDocs) {
            ObjectId fileId = fileDoc.getObjectId("_id");
            if (fileId == null) {
                continue;
            }
            fileIds.add(fileId);
            fileDocById.put(fileId, fileDoc);
            targetSnapshotByFileId.put(fileId, fileDoc.getInteger("currentSnapshotId", 0));
        }
        if (fileIds.isEmpty()) {
            return result;
        }

        Map<ObjectId, Long> sizeByFileId = resolveFileSizes(fileIds, targetSnapshotByFileId);
        for (ObjectId fileId : fileIds) {
            Document fileDoc = fileDocById.get(fileId);
            if (fileDoc == null) {
                continue;
            }
            ObjectId folderId = fileDoc.getObjectId("folderId");
            ObjectId workspaceId = folderId == null ? null : workspaceIdByFolderId.get(folderId);
            String workspaceName = workspaceNames.getOrDefault(workspaceId, "Workspace");
            String filename = safe(fileDoc.getString("filename"));
            result.add(new ProfileService.FileSizeEntry(
                    filename.isBlank() ? "Untitled file" : filename,
                    workspaceName,
                    sizeByFileId.getOrDefault(fileId, 0L)));
        }

        result.sort((left, right) -> Long.compare(right.sizeBytes(), left.sizeBytes()));
        if (result.size() > limit) {
            return new ArrayList<>(result.subList(0, limit));
        }
        return result;
    }

    private Map<ObjectId, Long> resolveFileSizes(
            List<ObjectId> fileIds,
            Map<ObjectId, Integer> targetSnapshotByFileId) {
        Map<ObjectId, Long> sizeByFileId = new HashMap<>();
        if (fileIds.isEmpty()) {
            return sizeByFileId;
        }

        List<Document> snapshotDocs = snapshots.find(new Document("fileId", new Document("$in", fileIds)))
                .sort(new Document("snapshotId", -1))
                .into(new ArrayList<>());
        for (Document snapshotDoc : snapshotDocs) {
            ObjectId fileId = snapshotDoc.getObjectId("fileId");
            if (fileId == null || sizeByFileId.containsKey(fileId)) {
                continue;
            }
            int targetSnapshot = targetSnapshotByFileId.getOrDefault(fileId, 0);
            int snapshotId = snapshotDoc.getInteger("snapshotId", 0);
            if (targetSnapshot > 0 && snapshotId != targetSnapshot) {
                continue;
            }
            sizeByFileId.put(fileId, extractSnapshotBytes(snapshotDoc));
        }

        for (ObjectId fileId : fileIds) {
            sizeByFileId.putIfAbsent(fileId, 0L);
        }
        return sizeByFileId;
    }

    private static long extractSnapshotBytes(Document snapshotDoc) {
        Document derivedStats = snapshotDoc.get("derivedStats", Document.class);
        if (derivedStats != null) {
            Number charCount = derivedStats.get("charCount", Number.class);
            if (charCount != null && charCount.longValue() > 0) {
                return charCount.longValue();
            }
        }

        String content = snapshotDoc.getString("content");
        if (content != null && !content.isEmpty()) {
            return content.getBytes(StandardCharsets.UTF_8).length;
        }
        return 0L;
    }

    private static Map<LocalDate, Integer> countDocumentsByDay(
            MongoCollection<Document> collection,
            ObjectId userId) {
        Map<LocalDate, Integer> counts = new LinkedHashMap<>();

        Instant from = LocalDate.now()
                .minusDays(29)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        Document query = new Document("createdBy", userId)
                .append("createdAt", new Document("$gte", java.util.Date.from(from)));

        for (Document doc : collection.find(query)) {
            java.util.Date createdAt = doc.getDate("createdAt");
            if (createdAt == null) {
                continue;
            }

            LocalDate date = createdAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            counts.put(date, counts.getOrDefault(date, 0) + 1);
        }
        return counts;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
