package com.dvcs.client.dashboard.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.dvcs.client.auth.repo.UserRepository;
import com.dvcs.client.dashboard.data.PendingRequestView;
import com.dvcs.client.dashboard.data.dao.CollaborationRequestDao;
import com.dvcs.client.dashboard.data.dao.FileDao;
import com.dvcs.client.dashboard.data.dao.FolderDao;
import com.dvcs.client.dashboard.data.dao.WorkspaceDao;
import com.dvcs.client.dashboard.notification.NotificationRequestItem;

public final class NotificationService {

    private final CollaborationRequestDao collaborationRequestDao;
    private final FileDao fileDao;
    private final FolderDao folderDao;
    private final WorkspaceDao workspaceDao;
    private final UserRepository userRepository;

    public NotificationService(
            CollaborationRequestDao collaborationRequestDao,
            FileDao fileDao,
            FolderDao folderDao,
            WorkspaceDao workspaceDao,
            UserRepository userRepository) {
        this.collaborationRequestDao = Objects.requireNonNull(collaborationRequestDao, "collaborationRequestDao");
        this.fileDao = Objects.requireNonNull(fileDao, "fileDao");
        this.folderDao = Objects.requireNonNull(folderDao, "folderDao");
        this.workspaceDao = Objects.requireNonNull(workspaceDao, "workspaceDao");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    public List<NotificationRequestItem> loadNotificationRequests(ObjectId currentUserId) {
        Objects.requireNonNull(currentUserId, "currentUserId");

        List<Document> pending = collaborationRequestDao.findPendingForUser(currentUserId);
        if (pending.isEmpty()) {
            return List.of();
        }

        Set<ObjectId> fileIds = pending.stream()
                .map(request -> request.getObjectId("fileId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<ObjectId, Document> fileById = new LinkedHashMap<>();
        for (Document file : fileDao.findByIds(new ArrayList<>(fileIds))) {
            ObjectId id = file.getObjectId("_id");
            if (id != null) {
                fileById.put(id, file);
            }
        }

        Set<ObjectId> folderIds = fileById.values().stream()
                .map(file -> file.getObjectId("folderId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<ObjectId, Document> folderById = new LinkedHashMap<>();
        for (Document folder : folderDao.findByIds(new ArrayList<>(folderIds))) {
            ObjectId id = folder.getObjectId("_id");
            if (id != null) {
                folderById.put(id, folder);
            }
        }

        Set<ObjectId> workspaceIds = folderById.values().stream()
                .map(folder -> folder.getObjectId("workspaceId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (Document request : pending) {
            ObjectId workspaceId = request.getObjectId("workspaceId");
            if (workspaceId != null) {
                workspaceIds.add(workspaceId);
            }
        }

        Map<ObjectId, Document> workspaceById = new LinkedHashMap<>();
        for (Document workspace : workspaceDao.findByIds(new ArrayList<>(workspaceIds))) {
            ObjectId id = workspace.getObjectId("_id");
            if (id != null) {
                workspaceById.put(id, workspace);
            }
        }

        List<NotificationRequestItem> items = new ArrayList<>(pending.size());
        for (Document request : pending) {
            ObjectId requestId = request.getObjectId("_id");
            ObjectId fileId = request.getObjectId("fileId");
            ObjectId requestedBy = request.getObjectId("requestedBy");

            ObjectId workspaceId = request.getObjectId("workspaceId");
            if (workspaceId == null && fileId != null) {
                Document file = fileById.get(fileId);
                if (file != null) {
                    ObjectId folderId = file.getObjectId("folderId");
                    Document folder = folderById.get(folderId);
                    if (folder != null) {
                        workspaceId = folder.getObjectId("workspaceId");
                    }
                }
            }

            String workspaceName = "Workspace";
            if (workspaceId != null) {
                Document workspace = workspaceById.get(workspaceId);
                if (workspace != null && workspace.getString("workspaceName") != null) {
                    workspaceName = workspace.getString("workspaceName");
                }
            }

            String requestedByUsername = requestedBy == null
                    ? "Unknown user"
                    : userRepository.findById(requestedBy)
                            .map(user -> user.getUsername())
                            .orElse("Unknown user");

            items.add(new NotificationRequestItem(
                    requestId,
                    fileId,
                    workspaceId,
                    workspaceName,
                    requestedByUsername));
        }

        return items;
    }

    public List<PendingRequestView> loadPendingRequests(ObjectId currentUserId) {
        Objects.requireNonNull(currentUserId, "currentUserId");

        List<Document> pending = collaborationRequestDao.findPendingForUser(currentUserId);
        List<PendingRequestView> views = new ArrayList<>(pending.size());
        for (Document request : pending) {
            ObjectId requestId = request.getObjectId("_id");
            ObjectId fileId = request.getObjectId("fileId");
            ObjectId requestedBy = request.getObjectId("requestedBy");

            String fileName = fileDao.findById(fileId)
                    .map(doc -> doc.getString("filename"))
                    .orElse("Unknown file");
            String requestedByUsername = userRepository.findById(requestedBy)
                    .map(user -> user.getUsername())
                    .orElse("Unknown user");

            views.add(new PendingRequestView(requestId, fileId, fileName, requestedByUsername));
        }
        return views;
    }

    public boolean acceptRequest(ObjectId requestId) {
        return collaborationRequestDao.updateStatus(requestId, "accepted");
    }

    public boolean rejectRequest(ObjectId requestId) {
        return collaborationRequestDao.updateStatus(requestId, "rejected");
    }
}
