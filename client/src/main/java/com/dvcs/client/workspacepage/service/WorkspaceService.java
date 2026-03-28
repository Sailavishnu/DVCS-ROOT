package com.dvcs.client.workspacepage.service;

import com.dvcs.client.workspacepage.dao.FileDAO;
import com.dvcs.client.workspacepage.dao.WorkspaceDAO;
import com.dvcs.client.workspacepage.model.FileItemModel;
import com.dvcs.client.workspacepage.model.FolderModel;
import com.dvcs.client.workspacepage.model.UserModel;
import com.dvcs.client.workspacepage.model.WorkspacePageModel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;

public final class WorkspaceService {

    private final WorkspaceDAO workspaceDAO;
    private final FileDAO fileDAO;
    private final CommitService commitService;

    public WorkspaceService(WorkspaceDAO workspaceDAO, FileDAO fileDAO, CommitService commitService) {
        this.workspaceDAO = Objects.requireNonNull(workspaceDAO, "workspaceDAO");
        this.fileDAO = Objects.requireNonNull(fileDAO, "fileDAO");
        this.commitService = Objects.requireNonNull(commitService, "commitService");
    }

    public WorkspacePageModel loadWorkspace(ObjectId workspaceId) {
        Document workspace = workspaceDAO.findWorkspaceById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));

        String workspaceName = safe(workspace.getString("workspaceName"));

        List<Document> folderDocs = workspaceDAO.findFoldersByWorkspaceId(workspaceId);
        List<ObjectId> folderIds = folderDocs.stream()
                .map(doc -> doc.getObjectId("_id"))
                .filter(Objects::nonNull)
                .toList();
        List<Document> fileDocs = workspaceDAO.findFilesByFolderIds(folderIds);

        Map<ObjectId, List<FileItemModel>> filesByFolder = new HashMap<>();
        for (Document fileDoc : fileDocs) {
            ObjectId fileId = fileDoc.getObjectId("_id");
            ObjectId folderId = fileDoc.getObjectId("folderId");
            String filename = safe(fileDoc.getString("filename"));

            Document latestCommit = fileDoc.get("latestCommit", Document.class);
            String latestMessage = latestCommit == null ? "No commits yet" : safe(latestCommit.getString("message"));
            Instant latestAt = null;
            if (latestCommit != null && latestCommit.getDate("committedAt") != null) {
                latestAt = latestCommit.getDate("committedAt").toInstant();
            }

            int snapshotId = fileDoc.getInteger("currentSnapshotId", 0);
            boolean isLocked = Boolean.TRUE.equals(fileDoc.getBoolean("isLocked", false));
            ObjectId lockedBy = fileDoc.getObjectId("lockedBy");

            FileItemModel fileItem = new FileItemModel(
                    fileId,
                    folderId,
                    "",
                    filename,
                    latestMessage,
                    latestAt,
                    snapshotId,
                    isLocked,
                    lockedBy);
            filesByFolder.computeIfAbsent(folderId, ignored -> new ArrayList<>()).add(fileItem);
        }

        List<FolderModel> folders = new ArrayList<>();
        for (Document folderDoc : folderDocs) {
            ObjectId folderId = folderDoc.getObjectId("_id");
            String folderName = safe(folderDoc.getString("folderName"));
            List<FileItemModel> folderFiles = new ArrayList<>(filesByFolder.getOrDefault(folderId, List.of()));
            folderFiles.sort(Comparator.comparing(FileItemModel::filename, String.CASE_INSENSITIVE_ORDER));

            List<FileItemModel> withFolderName = folderFiles.stream()
                    .map(file -> new FileItemModel(
                            file.fileId(),
                            file.folderId(),
                            folderName,
                            file.filename(),
                            file.latestCommitMessage(),
                            file.latestCommitAt(),
                            file.currentSnapshotId(),
                            file.locked(),
                            file.lockedBy()))
                    .toList();

            folders.add(new FolderModel(folderId, folderName, withFolderName));
        }
        folders.sort(Comparator.comparing(FolderModel::folderName, String.CASE_INSENSITIVE_ORDER));

        List<ObjectId> collaboratorIds = toObjectIdList(workspace.get("collaborators"));
        List<UserModel> collaborators = workspaceDAO.findUsersByIds(collaboratorIds).stream()
                .map(doc -> new UserModel(doc.getObjectId("_id"), safe(doc.getString("username"))))
                .sorted(Comparator.comparing(UserModel::username, String.CASE_INSENSITIVE_ORDER))
                .toList();

        String readmeContent = fileDAO.findRootReadme(workspaceId)
                .map(doc -> {
                    ObjectId fileId = doc.getObjectId("_id");
                    Integer snapshotId = doc.getInteger("currentSnapshotId", 0);
                    if (fileId == null) {
                        return "";
                    }
                    if (snapshotId != null && snapshotId > 0) {
                        return fileDAO.loadSnapshotContent(fileId, snapshotId);
                    }
                    return fileDAO.loadLatestContent(fileId);
                })
                .orElse("");

        int totalCommits = commitService.countWorkspaceCommits(workspaceId);

        return new WorkspacePageModel(workspaceId, workspaceName, folders, collaborators, totalCommits, readmeContent);
    }

    public Optional<FileItemModel> findFileByName(ObjectId workspaceId, String folderName, String filename) {
        if (filename == null || filename.isBlank()) {
            return Optional.empty();
        }
        WorkspacePageModel model = loadWorkspace(workspaceId);
        return model.folders().stream()
                .filter(folder -> folderName == null || folderName.isBlank()
                        || folder.folderName().equalsIgnoreCase(folderName))
                .flatMap(folder -> folder.files().stream())
                .filter(file -> file.filename().equalsIgnoreCase(filename))
                .findFirst();
    }

    public void updateWorkspaceName(ObjectId workspaceId, String newName) {
        String normalized = newName == null ? "" : newName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Workspace name is required");
        }
        workspaceDAO.updateWorkspaceName(workspaceId, normalized);
    }

    public List<UserModel> loadAllUsers() {
        return workspaceDAO.findAllUsers().stream()
                .map(doc -> new UserModel(doc.getObjectId("_id"), safe(doc.getString("username"))))
                .sorted(Comparator.comparing(UserModel::username, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public void addCollaborator(ObjectId workspaceId, ObjectId collaboratorId) {
        workspaceDAO.addCollaborator(workspaceId, collaboratorId);
    }

    public void removeCollaborator(ObjectId workspaceId, ObjectId collaboratorId) {
        workspaceDAO.removeCollaborator(workspaceId, collaboratorId);
    }

    public void deleteWorkspace(ObjectId workspaceId) {
        WorkspacePageModel model = loadWorkspace(workspaceId);
        List<ObjectId> folderIds = model.folders().stream()
                .map(FolderModel::folderId)
                .toList();
        List<ObjectId> fileIds = model.folders().stream()
                .flatMap(folder -> folder.files().stream())
                .map(FileItemModel::fileId)
                .toList();

        fileDAO.deleteWorkspaceRecords(fileIds);
        workspaceDAO.deleteWorkspaceCascade(workspaceId, folderIds, fileIds);
    }

    private static List<ObjectId> toObjectIdList(Object rawValue) {
        if (!(rawValue instanceof List<?> rawList)) {
            return List.of();
        }
        List<ObjectId> ids = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof ObjectId id) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
