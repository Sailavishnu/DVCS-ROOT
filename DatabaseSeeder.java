import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class DatabaseSeeder {

    // Pre-defined ObjectIds for consistent cross-references across all collections
    static final ObjectId USER_1        = new ObjectId("000000000000000000000001");
    static final ObjectId USER_2        = new ObjectId("000000000000000000000002");
    static final ObjectId WS_1          = new ObjectId("000000000000000000000010");
    static final ObjectId WS_2          = new ObjectId("000000000000000000000011");
    static final ObjectId FOLDER_1      = new ObjectId("000000000000000000000020"); // root in WS_1
    static final ObjectId FOLDER_2      = new ObjectId("000000000000000000000021"); // src  in WS_1
    static final ObjectId FOLDER_3      = new ObjectId("000000000000000000000022"); // root in WS_2
    static final ObjectId FILE_1        = new ObjectId("000000000000000000000030"); // README.md
    static final ObjectId FILE_2        = new ObjectId("000000000000000000000031"); // App.java
    static final ObjectId FILE_3        = new ObjectId("000000000000000000000032"); // notes.txt
    static final ObjectId FILE_4        = new ObjectId("000000000000000000000033"); // Main.java
    static final ObjectId COMMIT_1      = new ObjectId("000000000000000000000040");
    static final ObjectId COMMIT_2      = new ObjectId("000000000000000000000041");
    static final ObjectId BRANCH_1      = new ObjectId("000000000000000000000050");
    static final ObjectId BRANCH_2      = new ObjectId("000000000000000000000051");
    static final ObjectId TAG_1         = new ObjectId("000000000000000000000060");
    static final ObjectId TAG_2         = new ObjectId("000000000000000000000061");
    static final ObjectId MERGE_1       = new ObjectId("000000000000000000000070");
    static final ObjectId MERGE_2       = new ObjectId("000000000000000000000071");
    static final ObjectId COLAB_1       = new ObjectId("000000000000000000000080");
    static final ObjectId COLAB_2       = new ObjectId("000000000000000000000081");
    static final ObjectId NOTIF_1       = new ObjectId("000000000000000000000090");
    static final ObjectId NOTIF_2       = new ObjectId("000000000000000000000091");
    static final ObjectId MACHINE_1     = new ObjectId("0000000000000000000000a0");
    static final ObjectId MACHINE_2     = new ObjectId("0000000000000000000000a1");
    static final ObjectId SNAP_1        = new ObjectId("0000000000000000000000b0");
    static final ObjectId SNAP_2        = new ObjectId("0000000000000000000000b1");
    static final ObjectId FPERM_1       = new ObjectId("0000000000000000000000c0");
    static final ObjectId FPERM_2       = new ObjectId("0000000000000000000000c1");
    static final ObjectId AUDIT_1       = new ObjectId("0000000000000000000000d0");
    static final ObjectId AUDIT_2       = new ObjectId("0000000000000000000000d1");
    static final ObjectId ACTIVITY_1    = new ObjectId("0000000000000000000000e0");
    static final ObjectId ACTIVITY_2    = new ObjectId("0000000000000000000000e1");
    static final ObjectId REVIEW_1      = new ObjectId("0000000000000000000000f0");
    static final ObjectId REVIEW_2      = new ObjectId("0000000000000000000000f1");
    static final ObjectId SESSION_1     = new ObjectId("000000000000000000000100");
    static final ObjectId SESSION_2     = new ObjectId("000000000000000000000101");
    static final ObjectId STAR_1        = new ObjectId("000000000000000000000110");
    static final ObjectId STAR_2        = new ObjectId("000000000000000000000111");
    static final ObjectId SETTINGS_1    = new ObjectId("000000000000000000000120");
    static final ObjectId SETTINGS_2    = new ObjectId("000000000000000000000121");
    static final ObjectId COMMENT_1     = new ObjectId("000000000000000000000130");
    static final ObjectId COMMENT_2     = new ObjectId("000000000000000000000131");

    static Date daysAgo(int n) {
        return new Date(System.currentTimeMillis() - (long) n * 86_400_000L);
    }

    static Date daysFromNow(int n) {
        return new Date(System.currentTimeMillis() + (long) n * 86_400_000L);
    }

    public static void main(String[] args) {
        try (MongoClient client = MongoClients.create("mongodb://localhost:27017")) {
            MongoDatabase db = client.getDatabase("DVCS");

            System.out.println("Dropping all existing collections...");
            for (String name : db.listCollectionNames()) {
                db.getCollection(name).drop();
                System.out.println("  dropped: " + name);
            }

            System.out.println("\nSeeding 20 collections (2 documents each)...\n");

            // ── 1. users ──────────────────────────────────────────────────────────────
            db.getCollection("users").insertMany(List.of(
                new Document("_id", USER_1)
                    .append("username", "john_doe")
                    .append("displayName", "John Doe")
                    .append("name", new Document("firstName", "John").append("lastName", "Doe"))
                    .append("passwordHash", "hashed_password_1")
                    .append("role", "user")
                    .append("storageQuota", 5_000_000L)
                    .append("filesLimit", 100)
                    .append("lastActiveAt", new Date())
                    .append("collabStatus", "active")
                    .append("totalFilesShared", 3)
                    .append("createdAt", daysAgo(30)),
                new Document("_id", USER_2)
                    .append("username", "jane_smith")
                    .append("displayName", "Jane Smith")
                    .append("name", new Document("firstName", "Jane").append("lastName", "Smith"))
                    .append("passwordHash", "hashed_password_2")
                    .append("role", "collaborator")
                    .append("storageQuota", 2_000_000L)
                    .append("filesLimit", 50)
                    .append("lastActiveAt", daysAgo(1))
                    .append("collabStatus", "active")
                    .append("totalFilesShared", 1)
                    .append("createdAt", daysAgo(20))
            ));
            System.out.println("[1] users — seeded");

            // ── 2. workspaces ─────────────────────────────────────────────────────────
            db.getCollection("workspaces").insertMany(List.of(
                new Document("_id", WS_1)
                    .append("workspaceName", "MyProject")
                    .append("path", new Document("disk", "D:")
                        .append("folder", "dvcs-workspaces")
                        .append("folderName", "MyProject")
                        .append("absolutePath", "D:\\dvcs-workspaces\\MyProject"))
                    .append("createdBy", USER_1)
                    .append("collaborators", Arrays.asList(USER_2))
                    .append("createdAt", daysAgo(28))
                    .append("updatedAt", daysAgo(1)),
                new Document("_id", WS_2)
                    .append("workspaceName", "OpenSource")
                    .append("path", new Document("disk", "D:")
                        .append("folder", "dvcs-workspaces")
                        .append("folderName", "OpenSource")
                        .append("absolutePath", "D:\\dvcs-workspaces\\OpenSource"))
                    .append("createdBy", USER_2)
                    .append("collaborators", Arrays.asList(USER_1))
                    .append("createdAt", daysAgo(15))
                    .append("updatedAt", daysAgo(3))
            ));
            System.out.println("[2] workspaces — seeded");

            // ── 3. folders ────────────────────────────────────────────────────────────
            db.getCollection("folders").insertMany(List.of(
                new Document("_id", FOLDER_1)
                    .append("workspaceId", WS_1)
                    .append("folderName", "root")
                    .append("path", new Document("disk", "D:").append("folder", "dvcs-workspaces/MyProject"))
                    .append("createdBy", USER_1)
                    .append("createdAt", daysAgo(28)),
                new Document("_id", FOLDER_2)
                    .append("workspaceId", WS_1)
                    .append("folderName", "src")
                    .append("path", new Document("disk", "D:").append("folder", "dvcs-workspaces/MyProject/src"))
                    .append("createdBy", USER_1)
                    .append("createdAt", daysAgo(27))
            ));
            System.out.println("[3] folders — seeded");

            // ── 4. files ──────────────────────────────────────────────────────────────
            Document latestCommit1 = new Document("message", "Add README")
                .append("committedBy", USER_1).append("committedAt", daysAgo(5));
            Document latestCommit2 = new Document("message", "Initial scaffold")
                .append("committedBy", USER_1).append("committedAt", daysAgo(3));

            db.getCollection("files").insertMany(List.of(
                new Document("_id", FILE_1)
                    .append("folderId", FOLDER_1)
                    .append("filename", "README.md")
                    .append("extension", "md")
                    .append("path", new Document("disk", "D:")
                        .append("folder", "README.md")
                        .append("versionRoot", ".versions/README.md"))
                    .append("currentSnapshotId", 1)
                    .append("latestCommit", latestCommit1)
                    .append("lockStatus", new Document("isLocked", false)
                        .append("lockedBy", null).append("lockedAt", null))
                    .append("tags", Arrays.asList("docs", "readme"))
                    .append("createdBy", USER_1)
                    .append("createdAt", daysAgo(28)),
                new Document("_id", FILE_2)
                    .append("folderId", FOLDER_2)
                    .append("filename", "App.java")
                    .append("extension", "java")
                    .append("path", new Document("disk", "D:")
                        .append("folder", "src/App.java")
                        .append("versionRoot", ".versions/App.java"))
                    .append("currentSnapshotId", 1)
                    .append("latestCommit", latestCommit2)
                    .append("lockStatus", new Document("isLocked", true)
                        .append("lockedBy", USER_1).append("lockedAt", daysAgo(1)))
                    .append("tags", Arrays.asList("java", "core"))
                    .append("createdBy", USER_1)
                    .append("createdAt", daysAgo(27))
            ));
            System.out.println("[4] files — seeded");

            // ── 5. file_snapshots ─────────────────────────────────────────────────────
            db.getCollection("file_snapshots").insertMany(List.of(
                new Document("_id", SNAP_1)
                    .append("fileId", FILE_1)
                    .append("snapshotId", 1)
                    .append("content", "# MyProject\n\nA version-controlled Java project.\n")
                    .append("derivedStats", new Document("lineCount", 3).append("wordCount", 7).append("charCount", 42))
                    .append("createdAt", daysAgo(5)),
                new Document("_id", SNAP_2)
                    .append("fileId", FILE_2)
                    .append("snapshotId", 1)
                    .append("content", "public class App {\n    public static void main(String[] args) {\n        System.out.println(\"Hello DVCS\");\n    }\n}\n")
                    .append("derivedStats", new Document("lineCount", 5).append("wordCount", 9).append("charCount", 96))
                    .append("createdAt", daysAgo(3))
            ));
            System.out.println("[5] file_snapshots — seeded");

            // ── 6. commits ────────────────────────────────────────────────────────────
            db.getCollection("commits").insertMany(List.of(
                new Document("_id", COMMIT_1)
                    .append("fileId", FILE_1)
                    .append("workspaceId", WS_1)
                    .append("snapshotId", 1)
                    .append("message", "Add README")
                    .append("committedBy", USER_1)
                    .append("committedAt", daysAgo(5)),
                new Document("_id", COMMIT_2)
                    .append("fileId", FILE_2)
                    .append("workspaceId", WS_1)
                    .append("snapshotId", 1)
                    .append("message", "Initial scaffold")
                    .append("committedBy", USER_1)
                    .append("committedAt", daysAgo(3))
            ));
            System.out.println("[6] commits — seeded");

            // ── 7. branches ───────────────────────────────────────────────────────────
            db.getCollection("branches").insertMany(List.of(
                new Document("_id", BRANCH_1)
                    .append("workspaceId", WS_1)
                    .append("branchName", "main")
                    .append("description", "Primary production branch")
                    .append("createdBy", USER_1)
                    .append("isDefault", true)
                    .append("isProtected", true)
                    .append("status", "active")
                    .append("createdAt", daysAgo(28))
                    .append("updatedAt", daysAgo(3)),
                new Document("_id", BRANCH_2)
                    .append("workspaceId", WS_1)
                    .append("branchName", "develop")
                    .append("description", "Active development branch")
                    .append("createdBy", USER_1)
                    .append("isDefault", false)
                    .append("isProtected", false)
                    .append("status", "active")
                    .append("createdAt", daysAgo(20))
                    .append("updatedAt", daysAgo(1))
            ));
            System.out.println("[7] branches — seeded");

            // ── 8. tags ───────────────────────────────────────────────────────────────
            db.getCollection("tags").insertMany(List.of(
                new Document("_id", TAG_1)
                    .append("workspaceId", WS_1)
                    .append("tagName", "v1.0.0")
                    .append("description", "First stable release")
                    .append("commitId", COMMIT_1)
                    .append("targetBranch", "main")
                    .append("createdBy", USER_1)
                    .append("releaseNotes", "Initial public release.")
                    .append("createdAt", daysAgo(5)),
                new Document("_id", TAG_2)
                    .append("workspaceId", WS_1)
                    .append("tagName", "v0.9.0")
                    .append("description", "Beta release")
                    .append("commitId", COMMIT_2)
                    .append("targetBranch", "develop")
                    .append("createdBy", USER_1)
                    .append("releaseNotes", "Pre-release for internal testing.")
                    .append("createdAt", daysAgo(10))
            ));
            System.out.println("[8] tags — seeded");

            // ── 9. merge_requests ─────────────────────────────────────────────────────
            db.getCollection("merge_requests").insertMany(List.of(
                new Document("_id", MERGE_1)
                    .append("workspaceId", WS_1)
                    .append("title", "Add dark mode support")
                    .append("description", "Implements dual theme switching in UI")
                    .append("sourceBranch", "feature/dark-mode")
                    .append("targetBranch", "main")
                    .append("createdBy", USER_2)
                    .append("reviewers", Arrays.asList(USER_1))
                    .append("status", "open")
                    .append("labels", Arrays.asList("ui", "enhancement"))
                    .append("createdAt", daysAgo(2))
                    .append("updatedAt", daysAgo(1)),
                new Document("_id", MERGE_2)
                    .append("workspaceId", WS_1)
                    .append("title", "Fix null pointer in FileService")
                    .append("description", "Handles null content edge case on commit")
                    .append("sourceBranch", "fix/null-content")
                    .append("targetBranch", "main")
                    .append("createdBy", USER_1)
                    .append("reviewers", Arrays.asList(USER_2))
                    .append("status", "merged")
                    .append("labels", Arrays.asList("bug", "critical"))
                    .append("createdAt", daysAgo(7))
                    .append("updatedAt", daysAgo(5))
            ));
            System.out.println("[9] merge_requests — seeded");

            // ── 10. colab_requests ────────────────────────────────────────────────────
            db.getCollection("colab_requests").insertMany(List.of(
                new Document("_id", COLAB_1)
                    .append("fileId", FILE_2)
                    .append("workspaceId", WS_1)
                    .append("requestedBy", USER_2)
                    .append("requestedTo", USER_1)
                    .append("requestedAt", daysAgo(4))
                    .append("status", "pending")
                    .append("respondedAt", null),
                new Document("_id", COLAB_2)
                    .append("fileId", FILE_1)
                    .append("workspaceId", WS_1)
                    .append("requestedBy", USER_1)
                    .append("requestedTo", USER_2)
                    .append("requestedAt", daysAgo(10))
                    .append("status", "accepted")
                    .append("respondedAt", daysAgo(9))
            ));
            System.out.println("[10] colab_requests — seeded");

            // ── 11. folder_permissions ────────────────────────────────────────────────
            db.getCollection("folder_permissions").insertMany(List.of(
                new Document("_id", FPERM_1)
                    .append("folderId", FOLDER_1)
                    .append("userId", USER_2)
                    .append("permissionType", "read-write")
                    .append("grantedBy", USER_1)
                    .append("grantedAt", daysAgo(10))
                    .append("expiresAt", null)
                    .append("isActive", true),
                new Document("_id", FPERM_2)
                    .append("folderId", FOLDER_2)
                    .append("userId", USER_2)
                    .append("permissionType", "read")
                    .append("grantedBy", USER_1)
                    .append("grantedAt", daysAgo(8))
                    .append("expiresAt", daysFromNow(30))
                    .append("isActive", true)
            ));
            System.out.println("[11] folder_permissions — seeded");

            // ── 12. audit_logs ────────────────────────────────────────────────────────
            db.getCollection("audit_logs").insertMany(List.of(
                new Document("_id", AUDIT_1)
                    .append("userId", USER_1)
                    .append("action", "CREATE_FILE")
                    .append("entityType", "File")
                    .append("entityName", "App.java")
                    .append("entityId", FILE_2.toString())
                    .append("metadata", new Document("folder", "src").append("extension", "java"))
                    .append("loggedAt", daysAgo(27)),
                new Document("_id", AUDIT_2)
                    .append("userId", USER_1)
                    .append("action", "COMMIT")
                    .append("entityType", "File")
                    .append("entityName", "App.java")
                    .append("entityId", FILE_2.toString())
                    .append("metadata", new Document("snapshotId", 1).append("message", "Initial scaffold"))
                    .append("loggedAt", daysAgo(3))
            ));
            System.out.println("[12] audit_logs — seeded");

            // ── 13. notifications ─────────────────────────────────────────────────────
            db.getCollection("notifications").insertMany(List.of(
                new Document("_id", NOTIF_1)
                    .append("userId", USER_1)
                    .append("type", "collaboration_request")
                    .append("title", "Collaboration request")
                    .append("message", "jane_smith requested access to App.java")
                    .append("isRead", false)
                    .append("relatedEntityId", COLAB_1)
                    .append("relatedEntityType", "colab_request")
                    .append("createdAt", daysAgo(4)),
                new Document("_id", NOTIF_2)
                    .append("userId", USER_1)
                    .append("type", "merge_approved")
                    .append("title", "Merge request approved")
                    .append("message", "Your merge request 'Fix null pointer' was approved")
                    .append("isRead", true)
                    .append("relatedEntityId", MERGE_2)
                    .append("relatedEntityType", "merge_request")
                    .append("createdAt", daysAgo(5))
            ));
            System.out.println("[13] notifications — seeded");

            // ── 14. workspace_machines ────────────────────────────────────────────────
            db.getCollection("workspace_machines").insertMany(List.of(
                new Document("_id", MACHINE_1)
                    .append("workspaceId", WS_1)
                    .append("machineName", "DEV-PC")
                    .append("ipAddress", "192.168.1.10")
                    .append("osType", "Windows 11")
                    .append("registeredAt", daysAgo(28))
                    .append("lastAccessedAt", new Date())
                    .append("isActive", true),
                new Document("_id", MACHINE_2)
                    .append("workspaceId", WS_1)
                    .append("machineName", "LAPTOP")
                    .append("ipAddress", "192.168.1.25")
                    .append("osType", "Windows 10")
                    .append("registeredAt", daysAgo(14))
                    .append("lastAccessedAt", daysAgo(2))
                    .append("isActive", true)
            ));
            System.out.println("[14] workspace_machines — seeded");

            // ── 15. workspace_activity ────────────────────────────────────────────────
            db.getCollection("workspace_activity").insertMany(List.of(
                new Document("_id", ACTIVITY_1)
                    .append("workspaceId", WS_1)
                    .append("userId", USER_1)
                    .append("activityType", "commit")
                    .append("activityDate", daysAgo(0))
                    .append("count", 3)
                    .append("dayOfWeek", "Mon")
                    .append("weekNumber", 17)
                    .append("createdAt", new Date()),
                new Document("_id", ACTIVITY_2)
                    .append("workspaceId", WS_1)
                    .append("userId", USER_1)
                    .append("activityType", "commit")
                    .append("activityDate", daysAgo(1))
                    .append("count", 5)
                    .append("dayOfWeek", "Sun")
                    .append("weekNumber", 17)
                    .append("createdAt", daysAgo(1))
            ));
            System.out.println("[15] workspace_activity — seeded");

            // ── 16. file_reviews ──────────────────────────────────────────────────────
            db.getCollection("file_reviews").insertMany(List.of(
                new Document("_id", REVIEW_1)
                    .append("fileId", FILE_2)
                    .append("workspaceId", WS_1)
                    .append("reviewerId", USER_2)
                    .append("snapshotId", 1)
                    .append("reviewType", "code_review")
                    .append("status", "approved")
                    .append("comments", 2)
                    .append("score", 8)
                    .append("createdAt", daysAgo(3))
                    .append("updatedAt", daysAgo(2)),
                new Document("_id", REVIEW_2)
                    .append("fileId", FILE_1)
                    .append("workspaceId", WS_1)
                    .append("reviewerId", USER_1)
                    .append("snapshotId", 1)
                    .append("reviewType", "quality_check")
                    .append("status", "pending")
                    .append("comments", 0)
                    .append("score", null)
                    .append("createdAt", daysAgo(1))
                    .append("updatedAt", daysAgo(1))
            ));
            System.out.println("[16] file_reviews — seeded");

            // ── 17. user_sessions ─────────────────────────────────────────────────────
            db.getCollection("user_sessions").insertMany(List.of(
                new Document("_id", SESSION_1)
                    .append("userId", USER_1)
                    .append("sessionToken", "tok_a1b2c3d4e5f6a1b2c3d4e5f6")
                    .append("ipAddress", "192.168.1.10")
                    .append("userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .append("loginAt", daysAgo(1))
                    .append("expiresAt", daysFromNow(6))
                    .append("lastPingAt", new Date())
                    .append("isActive", true),
                new Document("_id", SESSION_2)
                    .append("userId", USER_2)
                    .append("sessionToken", "tok_f6e5d4c3b2a1f6e5d4c3b2a1")
                    .append("ipAddress", "192.168.1.25")
                    .append("userAgent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
                    .append("loginAt", daysAgo(2))
                    .append("expiresAt", daysFromNow(5))
                    .append("lastPingAt", daysAgo(1))
                    .append("isActive", true)
            ));
            System.out.println("[17] user_sessions — seeded");

            // ── 18. starred_workspaces ────────────────────────────────────────────────
            db.getCollection("starred_workspaces").insertMany(List.of(
                new Document("_id", STAR_1)
                    .append("userId", USER_2)
                    .append("workspaceId", WS_1)
                    .append("workspaceName", "MyProject")
                    .append("workspaceOwner", USER_1)
                    .append("starredAt", daysAgo(7))
                    .append("note", "Great reference for Java project structure")
                    .append("isPublic", true),
                new Document("_id", STAR_2)
                    .append("userId", USER_1)
                    .append("workspaceId", WS_2)
                    .append("workspaceName", "OpenSource")
                    .append("workspaceOwner", USER_2)
                    .append("starredAt", daysAgo(3))
                    .append("note", "")
                    .append("isPublic", false)
            ));
            System.out.println("[18] starred_workspaces — seeded");

            // ── 19. workspace_settings ────────────────────────────────────────────────
            db.getCollection("workspace_settings").insertMany(List.of(
                new Document("_id", SETTINGS_1)
                    .append("workspaceId", WS_1)
                    .append("defaultBranch", "main")
                    .append("visibility", "private")
                    .append("mergeStrategy", "merge")
                    .append("requireCodeReview", true)
                    .append("maxCollaborators", 10)
                    .append("theme", "dark")
                    .append("updatedAt", daysAgo(5)),
                new Document("_id", SETTINGS_2)
                    .append("workspaceId", WS_2)
                    .append("defaultBranch", "main")
                    .append("visibility", "team")
                    .append("mergeStrategy", "squash")
                    .append("requireCodeReview", false)
                    .append("maxCollaborators", 5)
                    .append("theme", "light")
                    .append("updatedAt", daysAgo(2))
            ));
            System.out.println("[19] workspace_settings — seeded");

            // ── 20. file_comments ─────────────────────────────────────────────────────
            db.getCollection("file_comments").insertMany(List.of(
                new Document("_id", COMMENT_1)
                    .append("fileId", FILE_2)
                    .append("workspaceId", WS_1)
                    .append("authorId", USER_2)
                    .append("snapshotId", 1)
                    .append("lineNumber", 3)
                    .append("content", "Consider extracting this into a utility method.")
                    .append("isResolved", false)
                    .append("replyTo", null)
                    .append("createdAt", daysAgo(2))
                    .append("updatedAt", daysAgo(2)),
                new Document("_id", COMMENT_2)
                    .append("fileId", FILE_2)
                    .append("workspaceId", WS_1)
                    .append("authorId", USER_1)
                    .append("snapshotId", 1)
                    .append("lineNumber", 3)
                    .append("content", "Good point — will refactor in the next commit.")
                    .append("isResolved", true)
                    .append("replyTo", COMMENT_1)
                    .append("createdAt", daysAgo(1))
                    .append("updatedAt", daysAgo(1))
            ));
            System.out.println("[20] file_comments — seeded");

            System.out.println("\n✅ All 20 collections seeded successfully (2 documents each, 40 total).");

        } catch (Exception e) {
            System.err.println("❌ Seeding failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
