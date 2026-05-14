package com.dvcs.client.dashboard.search;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.dvcs.client.auth.repo.UserRepository;
import com.dvcs.client.dashboard.data.dao.CommitDao;
import com.dvcs.client.dashboard.data.dao.FileDao;
import com.dvcs.client.dashboard.data.dao.FolderDao;
import com.dvcs.client.dashboard.data.dao.WorkspaceDao;

public final class SearchService {

    private final WorkspaceDao workspaceDao;
    private final FolderDao folderDao;
    private final FileDao fileDao;
    private final CommitDao commitDao;
    private final UserRepository userRepository;

    public SearchService(WorkspaceDao workspaceDao, FolderDao folderDao, FileDao fileDao,
            CommitDao commitDao, UserRepository userRepository) {
        this.workspaceDao = Objects.requireNonNull(workspaceDao);
        this.folderDao = Objects.requireNonNull(folderDao);
        this.fileDao = Objects.requireNonNull(fileDao);
        this.commitDao = Objects.requireNonNull(commitDao);
        this.userRepository = Objects.requireNonNull(userRepository);
    }

    // ── Unified multi-type search ─────────────────────────────────────────────

    public List<SearchResultItem> searchAll(ObjectId ownerId, String query,
            boolean includeFiles, boolean includeCommits, boolean includeWorkspaces) {
        Objects.requireNonNull(ownerId, "ownerId");

        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) return List.of();

        List<SearchResultItem> results = new ArrayList<>();

        if (includeFiles) results.addAll(searchFileResultsForOwner(ownerId, normalized));
        if (includeCommits) results.addAll(searchCommitResults(ownerId, normalized));
        if (includeWorkspaces) results.addAll(searchWorkspaceResults(ownerId, normalized));

        return results;
    }

    // ── File search ───────────────────────────────────────────────────────────

    public List<SearchResultItem> searchFileResultsForOwner(ObjectId ownerId, String query) {
        Objects.requireNonNull(ownerId, "ownerId");

        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) return List.of();

        List<Document> ownedWorkspaces = workspaceDao.findByCreator(ownerId);
        if (ownedWorkspaces.isEmpty()) return List.of();

        Map<ObjectId, Document> workspaceById = new LinkedHashMap<>();
        for (Document ws : ownedWorkspaces) {
            ObjectId id = ws.getObjectId("_id");
            if (id != null) workspaceById.put(id, ws);
        }

        List<Document> ownedFolders = folderDao.findByWorkspaceIds(new ArrayList<>(workspaceById.keySet()));
        Map<ObjectId, Document> folderById = new LinkedHashMap<>();
        for (Document f : ownedFolders) {
            ObjectId id = f.getObjectId("_id");
            if (id != null) folderById.put(id, f);
        }

        Set<ObjectId> matchedFolderIds = new LinkedHashSet<>();
        for (Document ws : workspaceDao.searchByWorkspaceName(normalized)) {
            ObjectId wsId = ws.getObjectId("_id");
            if (workspaceById.containsKey(wsId)) {
                for (Document folder : ownedFolders) {
                    if (wsId.equals(folder.getObjectId("workspaceId"))) {
                        ObjectId fid = folder.getObjectId("_id");
                        if (fid != null) matchedFolderIds.add(fid);
                    }
                }
            }
        }
        for (Document folder : folderDao.searchByFolderName(normalized)) {
            ObjectId fid = folder.getObjectId("_id");
            if (fid != null && folderById.containsKey(fid)) matchedFolderIds.add(fid);
        }

        Set<ObjectId> matchedFileIds = new LinkedHashSet<>();
        for (Document file : fileDao.searchByFileName(normalized)) {
            ObjectId fid = file.getObjectId("_id");
            ObjectId folderId = file.getObjectId("folderId");
            if (fid != null && folderById.containsKey(folderId)) matchedFileIds.add(fid);
        }
        for (Document file : fileDao.findByFolderIds(new ArrayList<>(matchedFolderIds))) {
            ObjectId fid = file.getObjectId("_id");
            if (fid != null) matchedFileIds.add(fid);
        }

        // Fetch latest commit per file for preview + metadata
        Map<ObjectId, Document> latestCommitByFileId = buildLatestCommitMap(
                new ArrayList<>(matchedFileIds));

        List<SearchResultItem> results = new ArrayList<>();
        for (Document file : fileDao.findByIds(new ArrayList<>(matchedFileIds))) {
            ObjectId fileId = file.getObjectId("_id");
            ObjectId folderId = file.getObjectId("folderId");
            Document folder = folderById.get(folderId);
            if (folder == null) continue;

            ObjectId wsId = folder.getObjectId("workspaceId");
            Document ws = workspaceById.get(wsId);
            if (ws == null) continue;

            String wsName = sanitize(ws.getString("workspaceName"));
            String folderName = sanitize(folder.getString("folderName"));
            String fileName = sanitize(file.getString("filename"));
            if (fileName.isEmpty()) continue;

            String relativePath = buildRelativePath(wsName, folderName, fileName);

            Document latestCommit = latestCommitByFileId.get(fileId);
            String preview = "";
            String committedBy = "";
            Instant lastModified = null;
            if (latestCommit != null) {
                preview = sanitize(latestCommit.getString("message"));
                ObjectId cById = latestCommit.getObjectId("committedBy");
                committedBy = cById == null ? "" :
                        userRepository.findById(cById).map(u -> u.getUsername()).orElse("");
                Date d = latestCommit.getDate("committedAt");
                lastModified = d != null ? d.toInstant() : null;
            }

            results.add(SearchResultItem.file(wsId, folderId, fileId,
                    wsName, folderName, fileName, relativePath,
                    preview, committedBy, lastModified));
        }

        results.sort(Comparator.comparing(SearchResultItem::relativePath, String.CASE_INSENSITIVE_ORDER));
        return results;
    }

    // ── Commit search ─────────────────────────────────────────────────────────

    public List<SearchResultItem> searchCommitResults(ObjectId ownerId, String query) {
        Objects.requireNonNull(ownerId, "ownerId");

        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) return List.of();

        List<Document> ownedWorkspaces = workspaceDao.findByCreator(ownerId);
        if (ownedWorkspaces.isEmpty()) return List.of();

        Map<ObjectId, Document> workspaceById = new LinkedHashMap<>();
        for (Document ws : ownedWorkspaces) {
            ObjectId id = ws.getObjectId("_id");
            if (id != null) workspaceById.put(id, ws);
        }

        List<Document> ownedFolders = folderDao.findByWorkspaceIds(new ArrayList<>(workspaceById.keySet()));
        Map<ObjectId, Document> folderById = new LinkedHashMap<>();
        for (Document f : ownedFolders) {
            ObjectId id = f.getObjectId("_id");
            if (id != null) folderById.put(id, f);
        }

        List<ObjectId> allFileIds = fileDao.findByFolderIds(
                new ArrayList<>(folderById.keySet())).stream()
                .map(f -> f.getObjectId("_id")).filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<ObjectId, Document> fileById = new LinkedHashMap<>();
        for (Document f : fileDao.findByIds(allFileIds)) {
            ObjectId id = f.getObjectId("_id");
            if (id != null) fileById.put(id, f);
        }

        List<SearchResultItem> results = new ArrayList<>();
        for (Document commit : commitDao.searchByMessage(normalized, allFileIds)) {
            ObjectId fileId = commit.getObjectId("fileId");
            ObjectId wsId = commit.getObjectId("workspaceId");
            String message = sanitize(commit.getString("message"));
            ObjectId committedBy = commit.getObjectId("committedBy");
            Date committedAt = commit.getDate("committedAt");

            Document file = fileId != null ? fileById.get(fileId) : null;
            Document folder = file != null ? folderById.get(file.getObjectId("folderId")) : null;
            Document ws = wsId != null ? workspaceById.get(wsId) : null;
            if (ws == null && folder != null) ws = workspaceById.get(folder.getObjectId("workspaceId"));

            String wsName = ws != null ? sanitize(ws.getString("workspaceName")) : "Workspace";
            String folderName = folder != null ? sanitize(folder.getString("folderName")) : "";
            String fileName = file != null ? sanitize(file.getString("filename")) : "Unknown file";
            String relativePath = buildRelativePath(wsName, folderName, fileName);

            String committer = committedBy == null ? "" :
                    userRepository.findById(committedBy).map(u -> u.getUsername()).orElse("");

            String hash = commit.getObjectId("_id") != null
                    ? commit.getObjectId("_id").toHexString().substring(0, 7) : "";

            Instant time = committedAt != null ? committedAt.toInstant() : null;
            ObjectId finalWsId = ws != null ? ws.getObjectId("_id") : wsId;
            ObjectId finalFolderId = folder != null ? folder.getObjectId("_id") : null;

            results.add(SearchResultItem.commit(finalWsId, finalFolderId, fileId,
                    wsName, folderName, fileName, relativePath,
                    message, hash, committer, time));
        }
        return results;
    }

    // ── Workspace search ──────────────────────────────────────────────────────

    public List<SearchResultItem> searchWorkspaceResults(ObjectId ownerId, String query) {
        Objects.requireNonNull(ownerId, "ownerId");

        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) return List.of();

        Set<ObjectId> ownedIds = workspaceDao.findByCreator(ownerId).stream()
                .map(d -> d.getObjectId("_id")).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Collect all matching workspaces: owned + public from others
        Map<ObjectId, Document> seen = new LinkedHashMap<>();
        for (Document ws : workspaceDao.searchByWorkspaceName(normalized)) {
            ObjectId wsId = ws.getObjectId("_id");
            if (wsId != null) seen.put(wsId, ws);
        }
        for (Document ws : workspaceDao.findPublicByName(normalized)) {
            ObjectId wsId = ws.getObjectId("_id");
            if (wsId != null) seen.put(wsId, ws);
        }

        List<SearchResultItem> results = new ArrayList<>();
        for (Document ws : seen.values()) {
            ObjectId wsId = ws.getObjectId("_id");
            // only include if owned by user OR marked public
            boolean isOwned = ownedIds.contains(wsId);
            boolean isPublic = "public".equals(ws.getString("visibility"));
            if (!isOwned && !isPublic) continue;

            String wsName = sanitize(ws.getString("workspaceName"));
            Date createdAt = ws.getDate("createdAt");
            Instant time = createdAt != null ? createdAt.toInstant() : null;
            long fileCount = fileDao.findByFolderIds(
                    folderDao.findByWorkspaceId(wsId).stream()
                            .map(f -> f.getObjectId("_id")).filter(Objects::nonNull)
                            .collect(Collectors.toList())).size();
            ObjectId wsOwner = ws.getObjectId("createdBy");
            results.add(SearchResultItem.workspace(wsId, wsOwner, wsName,
                    fileCount + " file" + (fileCount == 1 ? "" : "s"), time));
        }
        return results;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<ObjectId, Document> buildLatestCommitMap(List<ObjectId> fileIds) {
        Map<ObjectId, Document> map = new LinkedHashMap<>();
        for (Document commit : commitDao.findLatestByFileIds(fileIds)) {
            ObjectId fid = commit.getObjectId("fileId");
            if (fid != null && !map.containsKey(fid)) map.put(fid, commit);
        }
        return map;
    }

    private static String buildRelativePath(String ws, String folder, String file) {
        List<String> parts = new ArrayList<>();
        if (!ws.isEmpty()) parts.add(ws);
        if (!folder.isEmpty() && !"root".equalsIgnoreCase(folder)) {
            for (String p : folder.replace("\\", "/").split("/")) {
                if (!p.isBlank()) parts.add(p.trim());
            }
        }
        parts.add(file);
        return String.join("/", parts);
    }

    private static String sanitize(String v) {
        return v == null ? "" : v.trim();
    }
}
