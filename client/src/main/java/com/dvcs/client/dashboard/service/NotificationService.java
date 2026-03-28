package com.dvcs.client.dashboard.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.dvcs.client.auth.repo.UserRepository;
import com.dvcs.client.dashboard.data.PendingRequestView;
import com.dvcs.client.dashboard.data.dao.CollaborationRequestDao;
import com.dvcs.client.dashboard.data.dao.FileDao;

public final class NotificationService {

    private final CollaborationRequestDao collaborationRequestDao;
    private final FileDao fileDao;
    private final UserRepository userRepository;

    public NotificationService(
            CollaborationRequestDao collaborationRequestDao,
            FileDao fileDao,
            UserRepository userRepository) {
        this.collaborationRequestDao = Objects.requireNonNull(collaborationRequestDao, "collaborationRequestDao");
        this.fileDao = Objects.requireNonNull(fileDao, "fileDao");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
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
