package com.dvcs.client.dashboard.profile;

import com.dvcs.client.core.dao.AuditLogDao;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.mindrot.jbcrypt.BCrypt;

import com.dvcs.client.dashboard.profile.dao.CommitDAO;
import com.dvcs.client.dashboard.profile.dao.UserDAO;
import com.dvcs.client.dashboard.profile.dao.WorkspaceDAO;

public final class ProfileService {

    private final UserDAO userDAO;
    private final WorkspaceDAO workspaceDAO;
    private final CommitDAO commitDAO;
    private final AuditLogDao auditLogDao;

    public ProfileService(UserDAO userDAO, WorkspaceDAO workspaceDAO, CommitDAO commitDAO, AuditLogDao auditLogDao) {
        this.userDAO = Objects.requireNonNull(userDAO, "userDAO");
        this.workspaceDAO = Objects.requireNonNull(workspaceDAO, "workspaceDAO");
        this.commitDAO = Objects.requireNonNull(commitDAO, "commitDAO");
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao");
    }

    public ProfileViewModel loadProfile(ObjectId userId) {
        Objects.requireNonNull(userId, "userId");

        Document user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String username = safe(user.getString("username"));
        String name = user.getString("name");
        String bio = user.getString("bio");

        Number quotaNum = user.get("storageQuota", Number.class);
        Long storageQuota = quotaNum != null ? quotaNum.longValue() : null;

        List<ObjectId> workspaceIds = workspaceDAO.findWorkspaceIdsByCreator(userId);
        int totalWorkspaces = workspaceIds.size();
        int totalFolders = workspaceDAO.countFoldersByWorkspaceIds(workspaceIds);
        int totalFiles = workspaceDAO.countFilesByWorkspaceIds(workspaceIds);
        int totalCommits = commitDAO.countCommitsByWorkspaceIds(workspaceIds);

        Map<ObjectId, String> workspaceNames = workspaceDAO.findWorkspaceNamesByIds(workspaceIds);
        List<PopularWorkspace> popularWorkspaces = commitDAO.findTopPopularWorkspaces(workspaceIds, workspaceNames, 6);
        long storageUsedBytes = workspaceDAO.loadEstimatedStorageBytes(workspaceIds);
        List<FileSizeEntry> largestFiles = workspaceDAO.loadLargestFiles(workspaceIds, 6);

        Map<LocalDate, Integer> dailyCommitCounts = commitDAO.loadLast30DayCommitCounts(userId, workspaceIds);
        List<DayCommit> commitDays = buildLast30DayCommits(dailyCommitCounts);
        List<DayActivity> usageDays = buildLast30DayActivities(auditLogDao.loadDailyActionCounts(userId, 30));
        List<CreationDay> creationDays = buildLast30DayCreationSeries(
                workspaceDAO.loadLast30DayWorkspaceCreationCounts(userId),
                workspaceDAO.loadLast30DayFolderCreationCounts(userId),
                workspaceDAO.loadLast30DayFileCreationCounts(userId));

        List<Document> recentDocs = commitDAO.findRecent(workspaceIds, 10);
        List<RecentCommit> recentCommits = new ArrayList<>();
        for (Document doc : recentDocs) {
            String msg = doc.getString("message");
            ObjectId wsId = doc.getObjectId("workspaceId");
            String wsName = workspaceNames.getOrDefault(wsId, "Unknown");
            java.util.Date at = doc.getDate("committedAt");
            if (at == null) at = doc.getDate("createdAt");
            recentCommits.add(new RecentCommit(
                    msg != null ? msg : "",
                    wsName,
                    at != null ? at.toInstant() : null));
        }

        return new ProfileViewModel(
                userId,
                name,
                username,
                initials(name, username),
                bio,
                storageQuota,
                storageUsedBytes,
                popularWorkspaces,
                largestFiles,
                totalWorkspaces,
                totalFolders,
                totalFiles,
                totalCommits,
                commitDays,
                usageDays,
                creationDays,
                recentCommits);
    }

    public record PublicWorkspaceEntry(String workspaceName, String description) {}

    public List<PublicWorkspaceEntry> searchUserPublicWorkspaces(String targetUsername) {
        if (targetUsername == null || targetUsername.isBlank()) return List.of();
        Document userDoc = userDAO.findByUsername(targetUsername.trim()).orElse(null);
        if (userDoc == null) return List.of();
        ObjectId targetUserId = userDoc.getObjectId("_id");
        if (targetUserId == null) return List.of();
        List<Document> docs = workspaceDAO.findPublicWorkspacesByCreator(targetUserId);
        List<PublicWorkspaceEntry> result = new ArrayList<>();
        for (Document doc : docs) {
            String name = safe(doc.getString("workspaceName"));
            String desc = safe(doc.getString("description"));
            result.add(new PublicWorkspaceEntry(name.isBlank() ? "Untitled" : name, desc));
        }
        return result;
    }

    public UpdateResult updateProfile(ObjectId userId, String name, String username, String bio) {
        Objects.requireNonNull(userId, "userId");

        String normalizedUsername = safe(username).trim();
        String normalizedName = name == null || name.isBlank() ? null : name.trim();

        if (normalizedUsername.isEmpty()) {
            return UpdateResult.error("Username is required");
        }

        Document existing = userDAO.findById(userId)
                .orElse(null);
        if (existing == null) {
            return UpdateResult.error("User not found");
        }

        String currentUsername = safe(existing.getString("username"));
        if (!normalizedUsername.equalsIgnoreCase(currentUsername)
                && userDAO.existsByUsername(normalizedUsername)) {
            return UpdateResult.error("Username already exists");
        }

        boolean updated = userDAO.updateProfile(userId, normalizedName, normalizedUsername, null, bio);
        if (!updated) {
            return UpdateResult.error("No profile changes were saved");
        }

        return UpdateResult.success("Profile updated successfully");
    }

    public UpdateResult changePassword(ObjectId userId, String oldPassword, String newPassword,
            String confirmPassword) {
        Objects.requireNonNull(userId, "userId");

        Document existing = userDAO.findById(userId)
                .orElse(null);
        if (existing == null) {
            return UpdateResult.error("User not found");
        }

        if (safe(oldPassword).isBlank()) {
            return UpdateResult.error("Old password is required");
        }
        if (safe(newPassword).isBlank() || safe(confirmPassword).isBlank()) {
            return UpdateResult.error("New password and confirm password are required");
        }
        if (!newPassword.equals(confirmPassword)) {
            return UpdateResult.error("New password and confirm password must match");
        }

        String currentPasswordHash = safe(existing.getString("passwordHash"));
        if (currentPasswordHash.isBlank() || !BCrypt.checkpw(oldPassword, currentPasswordHash)) {
            return UpdateResult.error("Old password is incorrect");
        }

        String currentName = existing.getString("name");
        String currentUsername = safe(existing.getString("username"));
        String passwordHashToStore = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));

        boolean updated = userDAO.updateProfile(userId, currentName, currentUsername, passwordHashToStore, null);
        if (!updated) {
            return UpdateResult.error("Password could not be updated");
        }

        return UpdateResult.success("Password updated successfully");
    }

    public static String initials(String name, String username) {
        String source = (name != null && !name.isBlank()) ? name.trim() : safe(username).trim();
        if (source.isEmpty()) {
            return "U";
        }

        String[] parts = source.split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                initials.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            }
            if (initials.length() == 2) {
                break;
            }
        }

        if (initials.isEmpty()) {
            initials.append(source.substring(0, 1).toUpperCase(Locale.ROOT));
        }
        return initials.toString();
    }

    private static List<DayCommit> buildLast30DayCommits(Map<LocalDate, Integer> dailyCommitCounts) {
        List<DayCommit> days = new ArrayList<>(30);
        LocalDate today = LocalDate.now();
        for (int i = 29; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            int commits = dailyCommitCounts.getOrDefault(date, 0);
            days.add(new DayCommit(date, commits));
        }
        return days;
    }

    private static List<DayActivity> buildLast30DayActivities(Map<LocalDate, Integer> dailyActivityCounts) {
        List<DayActivity> days = new ArrayList<>(30);
        LocalDate today = LocalDate.now();
        for (int i = 29; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            int activities = dailyActivityCounts.getOrDefault(date, 0);
            days.add(new DayActivity(date, activities));
        }
        return days;
    }

    private static List<CreationDay> buildLast30DayCreationSeries(
            Map<LocalDate, Integer> workspaceCounts,
            Map<LocalDate, Integer> folderCounts,
            Map<LocalDate, Integer> fileCounts) {
        List<CreationDay> days = new ArrayList<>(30);
        LocalDate today = LocalDate.now();
        for (int i = 29; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            days.add(new CreationDay(
                    date,
                    workspaceCounts.getOrDefault(date, 0),
                    folderCounts.getOrDefault(date, 0),
                    fileCounts.getOrDefault(date, 0)));
        }
        return days;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record DayCommit(LocalDate date, int commitCount) {}

    public record DayActivity(LocalDate date, int activityCount) {}

    public record CreationDay(LocalDate date, int workspaceCount, int folderCount, int fileCount) {}

    public record PopularWorkspace(String workspaceName, int commitCount) {}

    public record FileSizeEntry(String filename, String workspaceName, long sizeBytes) {}

    public record RecentCommit(String message, String workspaceName, java.time.Instant time) {}

    public record ProfileViewModel(
            ObjectId userId,
            String name,
            String username,
            String initials,
            String bio,
            Long storageQuota,
            long storageUsedBytes,
            List<PopularWorkspace> popularWorkspaces,
            List<FileSizeEntry> largestFiles,
            int totalWorkspaces,
            int totalFolders,
            int totalFiles,
            int totalCommits,
            List<DayCommit> commitActivityDays,
            List<DayActivity> usageDays,
            List<CreationDay> creationDays,
            List<RecentCommit> recentCommits) {}


    public record UpdateResult(boolean success, String message) {
        public static UpdateResult success(String message) {
            return new UpdateResult(true, message);
        }

        public static UpdateResult error(String message) {
            return new UpdateResult(false, message);
        }
    }
}
