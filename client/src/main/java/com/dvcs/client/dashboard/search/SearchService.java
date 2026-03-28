package com.dvcs.client.dashboard.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.dvcs.client.dashboard.data.dao.FileDao;
import com.dvcs.client.dashboard.data.dao.FolderDao;
import com.dvcs.client.dashboard.data.dao.WorkspaceDao;

public final class SearchService {

    private final WorkspaceDao workspaceDao;
    private final FolderDao folderDao;
    private final FileDao fileDao;

    public SearchService(WorkspaceDao workspaceDao, FolderDao folderDao, FileDao fileDao) {
        this.workspaceDao = Objects.requireNonNull(workspaceDao, "workspaceDao");
        this.folderDao = Objects.requireNonNull(folderDao, "folderDao");
        this.fileDao = Objects.requireNonNull(fileDao, "fileDao");
    }

    public List<SearchResultItem> searchFileResultsForOwner(ObjectId ownerId, String query) {
        Objects.requireNonNull(ownerId, "ownerId");

        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<Document> ownedWorkspaces = workspaceDao.findByCreator(ownerId);
        if (ownedWorkspaces.isEmpty()) {
            return List.of();
        }

        Map<ObjectId, Document> workspaceById = new LinkedHashMap<>();
        for (Document workspace : ownedWorkspaces) {
            ObjectId id = workspace.getObjectId("_id");
            if (id != null) {
                workspaceById.put(id, workspace);
            }
        }
        if (workspaceById.isEmpty()) {
            return List.of();
        }

        List<Document> ownedFolders = folderDao.findByWorkspaceIds(new ArrayList<>(workspaceById.keySet()));
        Map<ObjectId, Document> folderById = new LinkedHashMap<>();
        for (Document folder : ownedFolders) {
            ObjectId folderId = folder.getObjectId("_id");
            if (folderId != null) {
                folderById.put(folderId, folder);
            }
        }

        Set<ObjectId> matchedWorkspaceIds = new LinkedHashSet<>();
        for (Document workspace : workspaceDao.searchByWorkspaceName(normalized)) {
            ObjectId workspaceId = workspace.getObjectId("_id");
            if (workspaceById.containsKey(workspaceId)) {
                matchedWorkspaceIds.add(workspaceId);
            }
        }

        Set<ObjectId> matchedFolderIds = new LinkedHashSet<>();
        for (Document folder : folderDao.searchByFolderName(normalized)) {
            ObjectId folderId = folder.getObjectId("_id");
            ObjectId workspaceId = folder.getObjectId("workspaceId");
            if (folderId != null && workspaceById.containsKey(workspaceId)) {
                matchedFolderIds.add(folderId);
            }
        }

        Set<ObjectId> matchedFileIds = new LinkedHashSet<>();
        for (Document file : fileDao.searchByFileName(normalized)) {
            ObjectId fileId = file.getObjectId("_id");
            ObjectId folderId = file.getObjectId("folderId");
            if (fileId != null && folderById.containsKey(folderId)) {
                matchedFileIds.add(fileId);
            }
        }

        for (Document folder : ownedFolders) {
            ObjectId folderId = folder.getObjectId("_id");
            ObjectId workspaceId = folder.getObjectId("workspaceId");
            if (folderId != null && matchedWorkspaceIds.contains(workspaceId)) {
                matchedFolderIds.add(folderId);
            }
        }

        List<Document> filesFromMatchedFolders = fileDao.findByFolderIds(new ArrayList<>(matchedFolderIds));
        for (Document file : filesFromMatchedFolders) {
            ObjectId fileId = file.getObjectId("_id");
            if (fileId != null) {
                matchedFileIds.add(fileId);
            }
        }

        List<Document> finalFileDocs = fileDao.findByIds(new ArrayList<>(matchedFileIds));
        List<SearchResultItem> results = new ArrayList<>();

        for (Document file : finalFileDocs) {
            ObjectId fileId = file.getObjectId("_id");
            ObjectId folderId = file.getObjectId("folderId");
            Document folder = folderById.get(folderId);
            if (folder == null) {
                continue;
            }

            ObjectId workspaceId = folder.getObjectId("workspaceId");
            Document workspace = workspaceById.get(workspaceId);
            if (workspace == null) {
                continue;
            }

            String workspaceName = sanitize(workspace.getString("workspaceName"));
            String folderName = sanitize(folder.getString("folderName"));
            String fileName = sanitize(file.getString("filename"));

            if (fileName.isEmpty()) {
                continue;
            }

            String relativePath = buildRelativePath(workspaceName, folderName, fileName);
            results.add(new SearchResultItem(
                    workspaceId,
                    folderId,
                    fileId,
                    workspaceName,
                    folderName,
                    fileName,
                    relativePath));
        }

        results.sort(Comparator.comparing(SearchResultItem::relativePath, String.CASE_INSENSITIVE_ORDER));
        return results;
    }

    private static String buildRelativePath(String workspaceName, String folderName, String fileName) {
        List<String> parts = new ArrayList<>();
        if (!workspaceName.isEmpty()) {
            parts.add(workspaceName);
        }
        if (!folderName.isEmpty() && !"root".equalsIgnoreCase(folderName)) {
            String[] folderParts = folderName.replace("\\", "/").split("/");
            for (String part : folderParts) {
                if (!part.isBlank()) {
                    parts.add(part.trim());
                }
            }
        }
        parts.add(fileName);
        return String.join("/", parts);
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
