package com.dvcs.client.workspacepage.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.dvcs.client.core.dao.AuditLogDao;
import com.dvcs.client.core.model.AuditLog;
import com.dvcs.client.core.model.Branch;
import com.dvcs.client.core.model.Tag;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.dvcs.client.workspacepage.dao.CommentDAO;
import com.dvcs.client.workspacepage.dao.FileDAO;
import com.dvcs.client.workspacepage.dao.WorkspaceDAO;
import com.dvcs.client.workspacepage.model.FileCommentModel;
import com.dvcs.client.workspacepage.model.FileItemModel;
import com.dvcs.client.workspacepage.model.FolderModel;
import com.dvcs.client.workspacepage.model.UserModel;
import com.dvcs.client.workspacepage.model.WorkspaceCommentModel;
import com.dvcs.client.workspacepage.model.WorkspacePageModel;

public final class WorkspaceService {

    private final WorkspaceDAO workspaceDAO;
    private final FileDAO fileDAO;
    private final CommentDAO commentDAO;
    private final AuditLogDao auditLogDao;
    private final CommitService commitService;

    public WorkspaceService(WorkspaceDAO workspaceDAO, FileDAO fileDAO, CommentDAO commentDAO, CommitService commitService, AuditLogDao auditLogDao) {
        this.workspaceDAO = Objects.requireNonNull(workspaceDAO, "workspaceDAO");
        this.fileDAO = Objects.requireNonNull(fileDAO, "fileDAO");
        this.commentDAO = Objects.requireNonNull(commentDAO, "commentDAO");
        this.commitService = Objects.requireNonNull(commitService, "commitService");
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao");
    }

    public WorkspacePageModel loadWorkspace(ObjectId workspaceId) {
        return loadWorkspace(workspaceId, null, true);
    }

    public WorkspacePageModel loadWorkspace(ObjectId workspaceId, ObjectId branchId, boolean isDefaultBranch) {
        Document workspace = workspaceDAO.findWorkspaceById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));

        String workspaceName = safe(workspace.getString("workspaceName"));
        String workspaceRootPath = resolveWorkspaceRoot(workspace);

        List<Document> folderDocs = workspaceDAO.findFoldersByWorkspaceId(workspaceId);
        List<ObjectId> folderIds = folderDocs.stream()
                .map(doc -> doc.getObjectId("_id"))
                .filter(Objects::nonNull)
                .toList();
        List<Document> fileDocs = branchId != null
                ? workspaceDAO.findFilesByFolderIdsAndBranch(folderIds, branchId, isDefaultBranch)
                : workspaceDAO.findFilesByFolderIds(folderIds);

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
            Document lockStatus = fileDoc.get("lockStatus", Document.class);
            boolean isLocked = lockStatus != null && Boolean.TRUE.equals(lockStatus.getBoolean("isLocked", false));
            ObjectId lockedBy = lockStatus != null ? lockStatus.getObjectId("lockedBy") : null;
            Instant lockedAt = null;
            if (lockStatus != null && lockStatus.getDate("lockedAt") != null) {
                lockedAt = lockStatus.getDate("lockedAt").toInstant();
            }

            FileItemModel fileItem = new FileItemModel(
                    fileId,
                    folderId,
                    "",
                    filename,
                    latestMessage,
                    latestAt,
                    snapshotId,
                    isLocked,
                    lockedBy,
                    lockedAt);
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
                            file.lockedBy(),
                            file.lockedAt()))
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

        List<Branch> branches = workspaceDAO.findBranchesByWorkspaceId(workspaceId).stream()
                .map(Branch::fromDocument)
                .sorted(Comparator.comparing(Branch::branchName))
                .toList();
        
        List<Tag> tags = workspaceDAO.findTagsByWorkspaceId(workspaceId).stream()
                .map(Tag::fromDocument)
                .sorted(Comparator.comparing(Tag::tagName))
                .toList();

        return new WorkspacePageModel(
                workspaceId,
                workspaceName,
                workspaceRootPath,
                folders,
                collaborators,
                totalCommits,
                readmeContent,
                branches,
                tags);
    }

    public String resolveWorkspaceRootPath(ObjectId workspaceId) {
        Document workspace = workspaceDAO.findWorkspaceById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        return resolveWorkspaceRoot(workspace);
    }

    public void ensureFolderMetadata(ObjectId workspaceId, ObjectId createdBy, String folderName) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(createdBy, "createdBy");

        String normalizedFolderName = normalizeFolderName(folderName);
        if (workspaceDAO.findFolderByWorkspaceIdAndName(workspaceId, normalizedFolderName).isPresent()) {
            return;
        }

        workspaceDAO.insertFolder(new Document("_id", new ObjectId())
                .append("workspaceId", workspaceId)
                .append("folderName", normalizedFolderName)
                .append("createdBy", createdBy)
                .append("createdAt", new Date()));

        auditLogDao.insert(new AuditLog(new ObjectId(), createdBy, "CREATE_FOLDER", "Folder",
                normalizedFolderName, null, Instant.now()));
    }

    public void ensureFileMetadata(ObjectId workspaceId, ObjectId createdBy, String folderName, String filename, ObjectId branchId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(createdBy, "createdBy");

        String normalizedFolderName = normalizeFolderName(folderName);
        String normalizedFilename = normalizeFileName(filename);

        ObjectId folderId = workspaceDAO.findFolderByWorkspaceIdAndName(workspaceId, normalizedFolderName)
                .map(doc -> doc.getObjectId("_id"))
                .filter(Objects::nonNull)
                .orElseGet(() -> workspaceDAO.insertFolder(new Document("_id", new ObjectId())
                        .append("workspaceId", workspaceId)
                        .append("folderName", normalizedFolderName)
                        .append("createdBy", createdBy)
                        .append("createdAt", new Date())));

        // For branch isolation: each branch can have its own copy of the file
        boolean exists = fileDAO.findFileByFolderIdAndName(folderId, normalizedFilename)
                .map(doc -> branchId == null || branchId.equals(doc.getObjectId("branchId")))
                .orElse(false);
        if (exists) {
            return;
        }

        String relativePath = "root".equalsIgnoreCase(normalizedFolderName)
                ? normalizedFilename
                : normalizedFolderName + "/" + normalizedFilename;

        fileDAO.insertFile(new Document("_id", new ObjectId())
                .append("folderId", folderId)
                .append("workspaceId", workspaceId)
                .append("branchId", branchId)
                .append("filename", normalizedFilename)
                .append("extension", extractExtension(normalizedFilename))
                .append("path", new Document("relativePath", relativePath)
                        .append("versionRoot", ".versions/" + normalizedFilename))
                .append("createdBy", createdBy)
                .append("createdAt", new Date())
                .append("currentSnapshotId", 0)
                .append("lockStatus", new Document("isLocked", false)
                        .append("lockedBy", null)
                        .append("lockedAt", null))
                .append("tags", new ArrayList<>()));

        auditLogDao.insert(new AuditLog(new ObjectId(), createdBy, "CREATE_FILE", "File",
                normalizedFilename, null, Instant.now()));
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

    public Optional<Document> findWorkspaceDocById(ObjectId workspaceId) {
        return workspaceDAO.findWorkspaceById(workspaceId);
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

    public void sendCollaboratorInvite(ObjectId currentUserId, ObjectId workspaceId, ObjectId collaboratorId, String permission) {
        workspaceDAO.createInvite(currentUserId, collaboratorId, workspaceId, permission);
    }

    public String getCollaboratorPermission(ObjectId workspaceId, ObjectId userId) {
        return workspaceDAO.getCollaboratorPermission(workspaceId, userId);
    }

    public void updateCollaboratorPermission(ObjectId workspaceId, ObjectId userId, String permission) {
        workspaceDAO.updateCollaboratorPermission(workspaceId, userId, permission);
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
        commentDAO.deleteWorkspaceComments(workspaceId);
        commentDAO.deleteFileComments(fileIds);
        workspaceDAO.deleteWorkspaceCascade(workspaceId, folderIds, fileIds);
    }

    public void renameFileMetadata(FileItemModel file, String newFilename, ObjectId performedBy) {
        Objects.requireNonNull(file, "file");
        String normalizedFilename = normalizeFileName(newFilename);
        String normalizedFolderName = normalizeFolderName(file.folderName());
        String relativePath = "root".equalsIgnoreCase(normalizedFolderName)
                ? normalizedFilename
                : normalizedFolderName + "/" + normalizedFilename;
        fileDAO.renameFile(file.fileId(), normalizedFilename, extractExtension(normalizedFilename), relativePath);

        auditLogDao.insert(new AuditLog(new ObjectId(), performedBy, "RENAME", "File",
                normalizedFilename, null, Instant.now()));
    }

    public void deleteFileMetadata(FileItemModel file, ObjectId performedBy) {
        if (file == null || file.fileId() == null) {
            return;
        }
        commentDAO.deleteFileComments(file.fileId());
        fileDAO.deleteFile(file.fileId());

        auditLogDao.insert(new AuditLog(new ObjectId(), performedBy, "DELETE", "File",
                file.filename(), null, Instant.now()));
    }

    public Branch ensureDefaultBranch(ObjectId workspaceId, ObjectId createdBy) {
        List<Document> existing = workspaceDAO.findBranchesByWorkspaceId(workspaceId);

        // If a branch with isDefault=true exists, return it
        Document existingDefault = existing.stream()
                .filter(d -> Boolean.TRUE.equals(d.getBoolean("isDefault", false)))
                .findFirst()
                .orElse(null);
        if (existingDefault != null) {
            return Branch.fromDocument(existingDefault);
        }

        // No default branch yet — check if "main" already exists by name
        Document mainByName = existing.stream()
                .filter(d -> "main".equalsIgnoreCase(d.getString("branchName")))
                .findFirst()
                .orElse(null);
        if (mainByName != null) {
            // Promote it to default
            workspaceDAO.markBranchAsDefault(mainByName.getObjectId("_id"));
            return Branch.fromDocument(mainByName.append("isDefault", true));
        }

        // Create "main" as default branch
        Branch main = new Branch(new ObjectId(), workspaceId, "main", "Default branch",
                createdBy, true, false, "active", Instant.now(), Instant.now());
        workspaceDAO.insertBranch(main.toDocument());
        return main;
    }

    public Branch createBranch(ObjectId workspaceId, String branchName, ObjectId createdBy) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(createdBy, "createdBy");
        String name = branchName == null ? "" : branchName.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Branch name is required");

        if (workspaceDAO.findBranchByName(workspaceId, name).isPresent()) {
            throw new IllegalArgumentException("Branch '" + name + "' already exists");
        }
        Branch branch = new Branch(new ObjectId(), workspaceId, name, null,
                createdBy, false, false, "active", Instant.now(), Instant.now());
        workspaceDAO.insertBranch(branch.toDocument());
        return branch;
    }

    public List<Branch> loadBranches(ObjectId workspaceId) {
        return workspaceDAO.findBranchesByWorkspaceId(workspaceId).stream()
                .map(Branch::fromDocument)
                .sorted(Comparator.comparing(Branch::isDefault).reversed()
                        .thenComparing(Branch::branchName))
                .toList();
    }

    public List<Document> loadCommitsByBranch(ObjectId workspaceId, ObjectId branchId, boolean isDefaultBranch) {
        return fileDAO.findCommitsByBranch(workspaceId, branchId, isDefaultBranch);
    }

    public List<WorkspaceCommentModel> loadWorkspaceComments(ObjectId workspaceId) {
        return commentDAO.findWorkspaceComments(workspaceId).stream()
                .map(doc -> new WorkspaceCommentModel(
                        doc.getObjectId("_id"),
                        doc.getObjectId("workspaceId"),
                        doc.getObjectId("authorId"),
                        safe(doc.getString("content")),
                        doc.getDate("createdAt") == null ? null : doc.getDate("createdAt").toInstant()))
                .toList();
    }

    public List<FileCommentModel> loadFileComments(ObjectId workspaceId) {
        return commentDAO.findFileComments(workspaceId).stream()
                .map(doc -> new FileCommentModel(
                        doc.getObjectId("_id"),
                        doc.getObjectId("workspaceId"),
                        doc.getObjectId("fileId"),
                        doc.getObjectId("authorId"),
                        safe(doc.getString("content")),
                        doc.getDate("createdAt") == null ? null : doc.getDate("createdAt").toInstant()))
                .toList();
    }

    public void addWorkspaceComment(ObjectId workspaceId, ObjectId authorId, String message) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(authorId, "authorId");
        commentDAO.insertWorkspaceComment(workspaceId, authorId, normalizeComment(message), new Date());
    }

    public void addFileComment(ObjectId workspaceId, ObjectId fileId, ObjectId authorId, String message) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(authorId, "authorId");
        commentDAO.insertFileComment(workspaceId, fileId, authorId, normalizeComment(message), new Date());
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

    private static String resolveWorkspaceRoot(Document workspace) {
        Document storagePath = workspace.get("path", Document.class);
        if (storagePath == null) {
            throw new IllegalStateException("Workspace path metadata is missing");
        }

        String absolutePath = safe(storagePath.getString("absolutePath"));
        if (!absolutePath.isBlank()) {
            return Path.of(absolutePath).toAbsolutePath().normalize().toString();
        }

        String drive = safe(storagePath.getString("disk"));
        String directory = safe(storagePath.getString("folder"));
        String folderName = safe(storagePath.getString("folderName"));
        Path pathPart = directory.isBlank() ? Path.of(folderName) : Path.of(directory, folderName);
        return Path.of(drive + pathPart).toAbsolutePath().normalize().toString();
    }

    private static String normalizeFolderName(String folderName) {
        String normalized = folderName == null ? "root" : folderName.trim();
        return normalized.isEmpty() ? "root" : normalized;
    }

    private static String normalizeFileName(String filename) {
        String normalized = filename == null ? "" : filename.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("File name is required");
        }
        return normalized;
    }

    private static String normalizeComment(String message) {
        String normalized = message == null ? "" : message.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Comment cannot be empty");
        }
        return normalized;
    }

    private static String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
