package com.dvcs.client.dashboard.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.dvcs.client.auth.model.User;
import com.dvcs.client.auth.repo.UserRepository;
import com.dvcs.client.dashboard.data.WorkspaceDetails;
import com.dvcs.client.dashboard.data.WorkspaceSummary;
import com.dvcs.client.dashboard.data.dao.CollaborationRequestDao;
import com.dvcs.client.dashboard.data.dao.FileDao;
import com.dvcs.client.dashboard.data.dao.FolderDao;
import com.dvcs.client.dashboard.data.dao.WorkspaceDao;

public final class WorkspaceService {

    private static final String DEFAULT_FOLDER_NAME = "root";

    private final WorkspaceDao workspaceDao;
    private final FolderDao folderDao;
    private final FileDao fileDao;
    private final CollaborationRequestDao collaborationRequestDao;
    private final UserRepository userRepository;

    public WorkspaceService(
            WorkspaceDao workspaceDao,
            FolderDao folderDao,
            FileDao fileDao,
            CollaborationRequestDao collaborationRequestDao,
            UserRepository userRepository) {
        this.workspaceDao = Objects.requireNonNull(workspaceDao, "workspaceDao");
        this.folderDao = Objects.requireNonNull(folderDao, "folderDao");
        this.fileDao = Objects.requireNonNull(fileDao, "fileDao");
        this.collaborationRequestDao = Objects.requireNonNull(collaborationRequestDao, "collaborationRequestDao");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    public Optional<ObjectId> findUserIdByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username.trim()).map(User::getId);
    }

    public WorkspaceSummary createWorkspace(ObjectId currentUserId, String workspaceName, Path selectedDirectory) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(workspaceName, "workspaceName");
        Objects.requireNonNull(selectedDirectory, "selectedDirectory");

        String normalizedName = workspaceName.trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Workspace name is required");
        }

        Path rootDirectory = selectedDirectory.toAbsolutePath().normalize();
        String folderName = sanitizeFolderName(normalizedName);
        Path workspaceDirectory = rootDirectory.resolve(folderName);

        try {
            Files.createDirectories(workspaceDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create workspace directory", e);
        }

        String drive = workspaceDirectory.getRoot() == null ? "" : workspaceDirectory.getRoot().toString();
        String directory = workspaceDirectory.getParent() == null
                ? ""
                : workspaceDirectory.getParent().toAbsolutePath().normalize().toString();

        Document workspace = new Document("_id", new ObjectId())
                .append("workspaceName", normalizedName)
                .append("storagePath", new Document("drive", drive)
                        .append("directory", directory)
                        .append("folderName", folderName)
                        .append("absolutePath", workspaceDirectory.toString()))
                .append("createdBy", currentUserId)
                .append("createdAt", new Date())
                .append("machines", List.of());

        ObjectId workspaceId = workspaceDao.insert(workspace);

        Document rootFolder = new Document("_id", new ObjectId())
                .append("workspaceId", workspaceId)
                .append("folderName", DEFAULT_FOLDER_NAME)
                .append("createdBy", currentUserId)
                .append("createdAt", new Date());
        folderDao.insert(rootFolder);

        return toWorkspaceSummary(workspace);
    }

    public int importFiles(
            ObjectId currentUserId,
            ObjectId workspaceId,
            String folderName,
            List<File> filesToImport) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(folderName, "folderName");
        Objects.requireNonNull(filesToImport, "filesToImport");

        if (filesToImport.isEmpty()) {
            return 0;
        }

        Document workspace = workspaceDao.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));

        Document path = workspace.get("storagePath", Document.class);
        if (path == null) {
            throw new IllegalStateException("Workspace storagePath metadata is missing");
        }

        String absoluteWorkspacePath = path.getString("absolutePath");
        if (absoluteWorkspacePath == null || absoluteWorkspacePath.isBlank()) {
            absoluteWorkspacePath = reconstructWorkspaceRoot(path);
        }

        Path workspaceRoot = Path.of(absoluteWorkspacePath).toAbsolutePath().normalize();
        Path targetFolder = workspaceRoot.resolve(folderName.trim());

        try {
            Files.createDirectories(targetFolder);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare target folder", e);
        }

        ObjectId folderId = resolveOrCreateFolder(workspaceId, folderName.trim(), currentUserId);

        int importedCount = 0;
        for (File selectedFile : filesToImport) {
            if (selectedFile == null || !selectedFile.exists() || !selectedFile.isFile()) {
                continue;
            }

            Path source = selectedFile.toPath();
            Path destination = targetFolder.resolve(source.getFileName().toString());
            try {
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to import file: " + selectedFile.getName(), e);
            }

            String filename = selectedFile.getName();
            String extension = extractExtension(filename);
            String relativePath = folderName.trim() + "/" + filename;

            Document fileDoc = new Document("_id", new ObjectId())
                    .append("folderId", folderId)
                    .append("filename", filename)
                    .append("extension", extension)
                    .append("path", new Document("relativePath", relativePath)
                            .append("versionRoot", ".versions/" + filename))
                    .append("createdBy", currentUserId)
                    .append("createdAt", new Date())
                    .append("isLocked", false)
                    .append("lockedBy", null)
                    .append("lockedAt", null)
                    .append("tags", List.of())
                    .append("snapshots", List.of());

            fileDao.insert(fileDoc);
            importedCount++;
        }

        return importedCount;
    }

    public List<WorkspaceSummary> loadOwnedWorkspaces(ObjectId currentUserId) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        List<Document> documents = workspaceDao.findByCreator(currentUserId);
        return documents.stream()
                .map(this::toWorkspaceSummary)
                .sorted(Comparator.comparing(WorkspaceSummary::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public Set<ObjectId> searchWorkspaceIdsByQuery(ObjectId currentUserId, String query) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        if (query == null || query.isBlank()) {
            return Set.of();
        }

        Set<ObjectId> ownedWorkspaceIds = loadOwnedWorkspaces(currentUserId).stream()
                .map(WorkspaceSummary::workspaceId)
                .collect(Collectors.toSet());

        Set<ObjectId> matchedWorkspaceIds = new HashSet<>();

        for (Document workspace : workspaceDao.searchByWorkspaceName(query.trim())) {
            ObjectId workspaceId = workspace.getObjectId("_id");
            if (ownedWorkspaceIds.contains(workspaceId)) {
                matchedWorkspaceIds.add(workspaceId);
            }
        }

        List<Document> matchedFolders = folderDao.searchByFolderName(query.trim());
        for (Document folder : matchedFolders) {
            ObjectId workspaceId = folder.getObjectId("workspaceId");
            if (ownedWorkspaceIds.contains(workspaceId)) {
                matchedWorkspaceIds.add(workspaceId);
            }
        }

        List<Document> ownedFolders = folderDao.findByWorkspaceIds(new ArrayList<>(ownedWorkspaceIds));
        Map<ObjectId, ObjectId> folderToWorkspace = new HashMap<>();
        for (Document folder : ownedFolders) {
            folderToWorkspace.put(folder.getObjectId("_id"), folder.getObjectId("workspaceId"));
        }

        for (Document file : fileDao.searchByFileName(query.trim())) {
            ObjectId folderId = file.getObjectId("folderId");
            ObjectId workspaceId = folderToWorkspace.get(folderId);
            if (workspaceId != null) {
                matchedWorkspaceIds.add(workspaceId);
            }
        }

        return matchedWorkspaceIds;
    }

    public List<WorkspaceSummary> loadCollaborativeWorkspaces(ObjectId currentUserId) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        List<Document> acceptedForUser = collaborationRequestDao.findByRequestedToAndStatus(currentUserId, "accepted");
        if (acceptedForUser.isEmpty()) {
            return List.of();
        }

        List<ObjectId> fileIds = acceptedForUser.stream()
                .map(request -> request.getObjectId("fileId"))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (fileIds.isEmpty()) {
            return List.of();
        }

        List<Document> files = fileDao.findByIds(fileIds);
        List<ObjectId> folderIds = files.stream()
                .map(file -> file.getObjectId("folderId"))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (folderIds.isEmpty()) {
            return List.of();
        }

        List<Document> folders = folderDao.findByIds(folderIds);
        List<ObjectId> workspaceIds = folders.stream()
                .map(folder -> folder.getObjectId("workspaceId"))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (workspaceIds.isEmpty()) {
            return List.of();
        }

        return workspaceDao.findByIds(workspaceIds).stream()
                .map(this::toWorkspaceSummary)
                .sorted(Comparator.comparing(WorkspaceSummary::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public WorkspaceDetails loadWorkspaceDetails(ObjectId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");

        Document workspace = workspaceDao.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));

        String workspaceName = workspace.getString("workspaceName");
        List<Document> folders = folderDao.findByWorkspaceId(workspaceId);
        List<String> folderNames = folders.stream()
                .map(folder -> folder.getString("folderName"))
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        List<ObjectId> folderIds = folders.stream().map(folder -> folder.getObjectId("_id")).toList();
        List<Document> files = fileDao.findByFolderIds(folderIds);
        List<String> fileNames = files.stream()
                .map(file -> file.getString("filename"))
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        List<ObjectId> fileIds = files.stream().map(file -> file.getObjectId("_id")).toList();
        List<Document> acceptedRequests = collaborationRequestDao.findByFileIdsAndStatus(fileIds, "accepted");
        Set<ObjectId> collaboratorIds = new HashSet<>();
        for (Document request : acceptedRequests) {
            ObjectId requestedBy = request.getObjectId("requestedBy");
            ObjectId requestedTo = request.getObjectId("requestedTo");
            if (requestedBy != null) {
                collaboratorIds.add(requestedBy);
            }
            if (requestedTo != null) {
                collaboratorIds.add(requestedTo);
            }
        }

        List<String> collaborators = collaboratorIds.stream()
                .map(userRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(User::getUsername)
                .sorted()
                .toList();

        return new WorkspaceDetails(workspaceName, folderNames, fileNames, collaborators);
    }

    public String reconstructFilePath(WorkspaceSummary workspace, String relativePath) {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(relativePath, "relativePath");

        String drive = workspace.drive() == null ? "" : workspace.drive();
        String directory = workspace.directory() == null ? "" : workspace.directory();
        String folderName = workspace.folderName() == null ? "" : workspace.folderName();

        Path basePath;
        if (!directory.isBlank()) {
            basePath = Path.of(directory, folderName);
        } else {
            basePath = Path.of(folderName);
        }

        String fullPath = drive + basePath.toString();
        return Path.of(fullPath, relativePath).normalize().toString();
    }

    private ObjectId resolveOrCreateFolder(ObjectId workspaceId, String folderName, ObjectId userId) {
        Document existing = folderDao.findByWorkspaceIdAndName(workspaceId, folderName);
        if (existing != null) {
            return existing.getObjectId("_id");
        }

        Document newFolder = new Document("_id", new ObjectId())
                .append("workspaceId", workspaceId)
                .append("folderName", folderName)
                .append("createdBy", userId)
                .append("createdAt", new Date());
        return folderDao.insert(newFolder);
    }

    private WorkspaceSummary toWorkspaceSummary(Document workspaceDoc) {
        ObjectId workspaceId = workspaceDoc.getObjectId("_id");
        String workspaceName = workspaceDoc.getString("workspaceName");

        Document storagePath = workspaceDoc.get("storagePath", Document.class);
        String drive = storagePath == null ? "" : storagePath.getString("drive");
        String directory = storagePath == null ? "" : storagePath.getString("directory");
        String folderName = storagePath == null ? "" : storagePath.getString("folderName");
        String absolutePath = storagePath == null ? "" : storagePath.getString("absolutePath");

        Date createdAtDate = workspaceDoc.getDate("createdAt");
        Instant createdAt = createdAtDate == null ? Instant.now() : createdAtDate.toInstant();

        return new WorkspaceSummary(workspaceId, workspaceName, drive, directory, folderName, absolutePath, createdAt);
    }

    private static String reconstructWorkspaceRoot(Document storagePath) {
        String drive = safe(storagePath.getString("drive"));
        String directory = safe(storagePath.getString("directory"));
        String folderName = safe(storagePath.getString("folderName"));

        Path dirPart = directory.isBlank() ? Path.of(folderName) : Path.of(directory, folderName);
        return drive + dirPart;
    }

    private static String sanitizeFolderName(String name) {
        String normalized = name.trim().replaceAll("[^a-zA-Z0-9._-]+", "_");
        if (normalized.isBlank()) {
            return "workspace_" + System.currentTimeMillis();
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
