package com.dvcs.client.dashboard.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.dvcs.client.auth.repo.UserRepository;
import com.dvcs.client.core.dao.NotificationDao;
import com.dvcs.client.core.model.ColabRequest;
import com.dvcs.client.core.model.Notification;
import com.dvcs.client.dashboard.data.dao.CollaborationRequestDao;
import com.dvcs.client.dashboard.data.dao.CommitDao;
import com.dvcs.client.dashboard.data.dao.FileDao;
import com.dvcs.client.dashboard.data.dao.FolderDao;
import com.dvcs.client.dashboard.data.dao.WorkspaceDao;
import com.dvcs.client.dashboard.notification.NotificationItem;
import com.dvcs.client.dashboard.notification.NotificationRequestItem;

public final class NotificationService {

    private final CollaborationRequestDao collaborationRequestDao;
    private final FileDao fileDao;
    private final FolderDao folderDao;
    private final WorkspaceDao workspaceDao;
    private final UserRepository userRepository;
    private final CommitDao commitDao;
    private final NotificationDao notificationDao;

    public NotificationService(
            CollaborationRequestDao collaborationRequestDao,
            FileDao fileDao,
            FolderDao folderDao,
            WorkspaceDao workspaceDao,
            UserRepository userRepository,
            CommitDao commitDao,
            NotificationDao notificationDao) {
        this.collaborationRequestDao = Objects.requireNonNull(collaborationRequestDao);
        this.fileDao = Objects.requireNonNull(fileDao);
        this.folderDao = Objects.requireNonNull(folderDao);
        this.workspaceDao = Objects.requireNonNull(workspaceDao);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.commitDao = Objects.requireNonNull(commitDao);
        this.notificationDao = Objects.requireNonNull(notificationDao);
    }

    // ── Unified feed ──────────────────────────────────────────────────────────

    public List<NotificationItem> loadAllNotifications(ObjectId currentUserId) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        List<NotificationItem> items = new ArrayList<>();

        // 1. Collaboration requests
        for (NotificationRequestItem r : loadNotificationRequests(currentUserId)) {
            items.add(NotificationItem.forCollabRequest(
                    r.requestId(), r.requestedByUsername(),
                    r.workspaceId(), r.workspaceName(), r.fileId()));
        }

        // 2. Recent commits from owned workspaces
        List<Document> ownedWorkspaces = workspaceDao.findByCreator(currentUserId);
        if (!ownedWorkspaces.isEmpty()) {
            List<ObjectId> wsIds = ownedWorkspaces.stream()
                    .map(d -> d.getObjectId("_id")).filter(Objects::nonNull)
                    .collect(Collectors.toList());

            Map<ObjectId, String> wsNames = new LinkedHashMap<>();
            for (Document ws : ownedWorkspaces) {
                ObjectId id = ws.getObjectId("_id");
                if (id != null) wsNames.put(id, ws.getString("workspaceName"));
            }

            for (Document commit : commitDao.findRecentByWorkspaceIds(wsIds, 15)) {
                ObjectId fileId = commit.getObjectId("fileId");
                ObjectId wsId = commit.getObjectId("workspaceId");
                String message = commit.getString("message");
                ObjectId committedBy = commit.getObjectId("committedBy");
                Date committedAt = commit.getDate("committedAt");

                String fileName = fileId == null ? "Unknown file" :
                        fileDao.findById(fileId).map(d -> d.getString("filename"))
                                .orElse("Unknown file");

                String committer = committedBy == null ? "Unknown" :
                        userRepository.findById(committedBy)
                                .map(u -> u.getUsername()).orElse("Unknown");

                String wsName = wsId != null ? wsNames.getOrDefault(wsId, "Workspace") : "Workspace";
                String hash = commit.getObjectId("_id") != null
                        ? commit.getObjectId("_id").toHexString().substring(0, 7) : "";
                Instant time = committedAt != null ? committedAt.toInstant() : Instant.now();

                items.add(NotificationItem.forCommit(
                        fileName, message != null ? message : "No message",
                        committer, wsId, wsName, hash, time));
            }
        }

        // Collab requests first, then most-recent commits
        items.sort((a, b) -> {
            boolean ac = "COLLAB_REQUEST".equals(a.type());
            boolean bc = "COLLAB_REQUEST".equals(b.type());
            if (ac && !bc) return -1;
            if (!ac && bc) return 1;
            if (a.time() == null) return 1;
            if (b.time() == null) return -1;
            return b.time().compareTo(a.time());
        });

        return items;
    }

    public int countPendingRequests(ObjectId currentUserId) {
        return loadNotificationRequests(currentUserId).size();
    }

    // ── Legacy collab-request loader ──────────────────────────────────────────

    public List<NotificationRequestItem> loadNotificationRequests(ObjectId currentUserId) {
        Objects.requireNonNull(currentUserId, "currentUserId");

        List<ColabRequest> pending = collaborationRequestDao.findPendingForUser(currentUserId);
        if (pending.isEmpty()) return List.of();

        Set<ObjectId> fileIds = pending.stream()
                .map(ColabRequest::fileId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<ObjectId, Document> fileById = new LinkedHashMap<>();
        for (Document f : fileDao.findByIds(new ArrayList<>(fileIds))) {
            ObjectId id = f.getObjectId("_id");
            if (id != null) fileById.put(id, f);
        }

        Set<ObjectId> folderIds = fileById.values().stream()
                .map(f -> f.getObjectId("folderId")).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<ObjectId, Document> folderById = new LinkedHashMap<>();
        for (Document folder : folderDao.findByIds(new ArrayList<>(folderIds))) {
            ObjectId id = folder.getObjectId("_id");
            if (id != null) folderById.put(id, folder);
        }

        Set<ObjectId> workspaceIds = folderById.values().stream()
                .map(f -> f.getObjectId("workspaceId")).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<ObjectId, Document> workspaceById = new LinkedHashMap<>();
        for (Document ws : workspaceDao.findByIds(new ArrayList<>(workspaceIds))) {
            ObjectId id = ws.getObjectId("_id");
            if (id != null) workspaceById.put(id, ws);
        }

        List<NotificationRequestItem> items = new ArrayList<>(pending.size());
        for (ColabRequest r : pending) {
            ObjectId fileId = r.fileId();
            ObjectId workspaceId = r.workspaceId(); // workspace-level invites have this set directly
            if (workspaceId == null && fileId != null) {
                // file-level invite: chain fileId → folder → workspace
                Document file = fileById.get(fileId);
                if (file != null) {
                    Document folder = folderById.get(file.getObjectId("folderId"));
                    if (folder != null) workspaceId = folder.getObjectId("workspaceId");
                }
            }
            String workspaceName = "Workspace";
            if (workspaceId != null) {
                Document ws = workspaceById.get(workspaceId);
                if (ws == null) {
                    // not in pre-loaded map (workspace-level invites skipped fileId chain)
                    ws = workspaceDao.findById(workspaceId).orElse(null);
                }
                if (ws != null && ws.getString("workspaceName") != null)
                    workspaceName = ws.getString("workspaceName");
            }
            String requestedByUsername = r.requestedBy() == null ? "Unknown user" :
                    userRepository.findById(r.requestedBy())
                            .map(u -> u.getUsername()).orElse("Unknown user");

            items.add(new NotificationRequestItem(
                    r.id(), fileId, workspaceId, workspaceName, requestedByUsername));
        }
        return items;
    }

    public boolean acceptRequest(ObjectId requestId) {
        Optional<ColabRequest> requestOpt = collaborationRequestDao.findById(requestId);
        if (requestOpt.isPresent() && collaborationRequestDao.updateStatus(requestId, "accepted")) {
            ColabRequest request = requestOpt.get();
            if (request.workspaceId() != null) {
                workspaceDao.addCollaborator(request.workspaceId(), request.requestedTo());
            }
            if (request.requestedBy() != null) {
                notificationDao.insert(new Notification(
                        new org.bson.types.ObjectId(), request.requestedBy(),
                        "COLLAB_ACCEPTED", "Collaboration request accepted",
                        "Your collaboration request was accepted.", false,
                        request.workspaceId(), Instant.now()));
            }
            return true;
        }
        return false;
    }

    public boolean rejectRequest(ObjectId requestId) {
        Optional<ColabRequest> requestOpt = collaborationRequestDao.findById(requestId);
        boolean updated = collaborationRequestDao.updateStatus(requestId, "rejected");
        if (updated && requestOpt.isPresent()) {
            ColabRequest request = requestOpt.get();
            if (request.requestedBy() != null) {
                notificationDao.insert(new Notification(
                        new org.bson.types.ObjectId(), request.requestedBy(),
                        "COLLAB_REJECTED", "Collaboration request rejected",
                        "Your collaboration request was rejected.", false,
                        request.workspaceId(), Instant.now()));
            }
        }
        return updated;
    }
}
