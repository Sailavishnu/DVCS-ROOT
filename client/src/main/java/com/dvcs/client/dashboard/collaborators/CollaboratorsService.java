package com.dvcs.client.dashboard.collaborators;

import com.dvcs.client.auth.model.User;
import com.dvcs.client.auth.repo.UserRepository;
import com.dvcs.client.core.model.ColabRequest;
import com.dvcs.client.dashboard.data.dao.CollaborationRequestDao;
import com.dvcs.client.dashboard.data.dao.CommitDao;
import com.dvcs.client.dashboard.data.dao.WorkspaceDao;
import com.dvcs.client.dashboard.profile.ProfileService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;

public final class CollaboratorsService {

    private final CollaborationRequestDao collaborationRequestDao;
    private final UserRepository userRepository;
    private final WorkspaceDao workspaceDao;
    private final CommitDao commitDao;

    public CollaboratorsService(
            CollaborationRequestDao collaborationRequestDao,
            UserRepository userRepository,
            WorkspaceDao workspaceDao,
            CommitDao commitDao) {
        this.collaborationRequestDao = Objects.requireNonNull(collaborationRequestDao);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.workspaceDao = Objects.requireNonNull(workspaceDao);
        this.commitDao = Objects.requireNonNull(commitDao);
    }

    public List<CollaboratorItem> loadCollaborators(ObjectId currentUserId) {
        Objects.requireNonNull(currentUserId, "currentUserId");
        List<ColabRequest> accepted = collaborationRequestDao.findAcceptedBothDirections(currentUserId);
        List<CollaboratorItem> result = new ArrayList<>();

        for (ColabRequest req : accepted) {
            ObjectId otherUserId = req.requestedBy().equals(currentUserId)
                    ? req.requestedTo() : req.requestedBy();
            Optional<User> otherUserOpt = userRepository.findById(otherUserId);
            if (otherUserOpt.isEmpty()) continue;
            User other = otherUserOpt.get();

            String workspaceName = "Unknown";
            if (req.workspaceId() != null) {
                Optional<Document> ws = workspaceDao.findById(req.workspaceId());
                if (ws.isPresent()) workspaceName = ws.get().getString("workspaceName");
            }

            List<String> commitMsgs = new ArrayList<>();
            List<Instant> commitTimes = new ArrayList<>();
            if (req.workspaceId() != null) {
                for (Document c : commitDao.findRecentByWorkspaceIds(List.of(req.workspaceId()), 2)) {
                    commitMsgs.add(c.getString("message"));
                    Date d = c.getDate("committedAt");
                    commitTimes.add(d != null ? d.toInstant() : null);
                }
            }

            String status = deriveStatus(other.getLastActiveAt());
            Instant since = req.respondedAt() != null ? req.respondedAt() : req.requestedAt();

            result.add(new CollaboratorItem(
                    other.getUsername(),
                    ProfileService.initials(null, other.getUsername()),
                    workspaceName,
                    status,
                    since,
                    commitMsgs,
                    commitTimes));
        }
        return result;
    }

    public void createInvite(ObjectId currentUserId, String targetUsername, String workspaceName)
            throws IllegalArgumentException {
        Optional<User> target = userRepository.findByUsername(targetUsername);
        if (target.isEmpty()) throw new IllegalArgumentException("User '" + targetUsername + "' not found");

        List<Document> myWorkspaces = workspaceDao.findByCreator(currentUserId);
        ObjectId workspaceId = null;
        for (Document ws : myWorkspaces) {
            if (workspaceName.equals(ws.getString("workspaceName"))) {
                workspaceId = ws.getObjectId("_id");
                break;
            }
        }
        if (workspaceId == null)
            throw new IllegalArgumentException("Workspace '" + workspaceName + "' not found in your workspaces");

        collaborationRequestDao.createInvite(currentUserId, target.get().getId(), workspaceId);
    }

    public long countOnline(List<CollaboratorItem> collaborators) {
        return collaborators.stream().filter(c -> !"Offline".equals(c.status())).count();
    }

    private static String deriveStatus(Instant lastActiveAt) {
        if (lastActiveAt == null) return "Offline";
        long secondsAgo = Instant.now().getEpochSecond() - lastActiveAt.getEpochSecond();
        if (secondsAgo < 3600) return "Syncing";
        if (secondsAgo < 86400) return "Active";
        if (secondsAgo < 604800) return "Idle";
        return "Offline";
    }
}
