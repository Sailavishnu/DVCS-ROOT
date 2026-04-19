import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Arrays;
import java.util.Date;

public class DatabaseSeeder {

    public static void main(String[] args) {
        String uri = "mongodb://localhost:27017";
        System.out.println("Connecting to MongoDB...");
        
        try (MongoClient mongoClient = MongoClients.create(uri)) {
            MongoDatabase database = mongoClient.getDatabase("DVCS");

            System.out.println("Dropping existing collections to wipe old data...");
            for (String collectionName : database.listCollectionNames()) {
                database.getCollection(collectionName).drop();
                System.out.println(" -> Dropped: " + collectionName);
            }

            System.out.println("\nInserting new structured dummy data (with derived and compound attributes)...");
            
            // 1. Users
            ObjectId userId1 = new ObjectId();
            Document user1 = new Document("_id", userId1)
                    .append("username", "john_doe")
                    // Compound attribute for Name
                    .append("name", new Document("firstName", "John").append("lastName", "Doe"))
                    .append("passwordHash", "hashed123")
                    .append("role", "user")
                    .append("storageQuota", 5000000L)
                    .append("filesLimit", 100)
                    .append("lastActiveAt", new Date())
                    // Derived attribute representation
                    .append("derivedDaysSinceLastActive", 0)
                    .append("collabStatus", "active")
                    .append("totalFilesShared", 5)
                    .append("createdAt", new Date());
            database.getCollection("users").insertOne(user1);

            // 2. Workspaces
            ObjectId workspaceId = new ObjectId();
            Document workspace = new Document("_id", workspaceId)
                    .append("workspaceName", "Java_Project")
                    // Compound attribute for Path
                    .append("path", new Document("disk", "D:").append("folder", "dvcs-root"))
                    .append("folderName", "MainWorkspace")
                    .append("createdBy", userId1)
                    .append("createdAt", new Date());
            database.getCollection("workspaces").insertOne(workspace);

            // 3. Workspace Machines
            Document machine = new Document("workspaceId", workspaceId)
                    .append("machineName", "DEV-MACHINE-1")
                    .append("ipAddress", "192.168.0.100")
                    .append("registeredAt", new Date())
                    .append("lastAccessedAt", new Date());
            database.getCollection("workspace_machines").insertOne(machine);

            // 4. Folders
            ObjectId folderId = new ObjectId();
            Document folder = new Document("_id", folderId)
                    .append("workspaceId", workspaceId)
                    .append("folderName", "src")
                    // Compound attribute for Path
                    .append("path", new Document("disk", "D:").append("folder", "dvcs-root/src"))
                    .append("createdBy", userId1)
                    .append("createdAt", new Date());
            database.getCollection("folders").insertOne(folder);

            // 5. Files
            ObjectId fileId = new ObjectId();
            Document file = new Document("_id", fileId)
                    .append("folderId", folderId)
                    .append("filename", "App.java")
                    .append("extension", ".java")
                    .append("createdBy", userId1)
                    .append("createdAt", new Date())
                    // Compound attribute for Lock Status
                    .append("lockStatus", new Document("isLocked", true)
                            .append("lockedBy", userId1)
                            .append("lockedAt", new Date()))
                    // Multi-valued attribute for Tags
                    .append("tags", Arrays.asList("java", "backend", "core"));
            database.getCollection("files").insertOne(file);

            // 6. File Snapshots
            Document snapshot = new Document("fileId", fileId)
                    .append("snapshotId", 1)
                    .append("content", "public class App {\n    public static void main(String[] args) {}\n}")
                    // Compound attribute for Path
                    .append("path", new Document("disk", "D:").append("folder", "dvcs-root/snapshots/App_1.java"))
                    .append("versionNumber", 1)
                    // Derived attributes 
                    .append("derivedStats", new Document("lineCount", 3).append("wordCount", 10))
                    .append("createdAt", new Date());
            database.getCollection("file_snapshots").insertOne(snapshot);

            // 7. Commits
            Document commit = new Document("fileId", fileId)
                    .append("snapshotId", 1)
                    .append("committedBy", userId1)
                    .append("commitMessage", "Initial project scaffold")
                    .append("committedAt", new Date());
            database.getCollection("commits").insertOne(commit);
            
            // 8. extended DB collections
            Document branch = new Document("workspaceId", workspaceId)
                    .append("branchName", "main")
                    .append("description", "Primary production branch")
                    .append("createdBy", userId1)
                    .append("isDefault", true)
                    .append("isProtected", true)
                    .append("status", "active")
                    .append("createdAt", new Date())
                    .append("updatedAt", new Date());
            database.getCollection("branches").insertOne(branch);

            // 9. Colab Requests
            Document colabReq = new Document("fileId", fileId)
                    .append("requestedBy", userId1)
                    .append("requestedTo", new ObjectId())
                    .append("requestedAt", new Date())
                    .append("status", "pending")
                    .append("respondedAt", null);
            database.getCollection("colab_requests").insertOne(colabReq);

            // 10. Folder Permissions
            Document folderPerm = new Document("folderId", folderId)
                    .append("userId", userId1)
                    .append("permissionType", "read-write")
                    .append("grantedBy", userId1)
                    .append("grantedAt", new Date());
            database.getCollection("folder_permissions").insertOne(folderPerm);

            // 11. Audit Logs
            Document auditLog = new Document("userId", userId1)
                    .append("action", "FILE_LOCKED")
                    .append("entityAffected", "Files")
                    .append("entityId", fileId.toString())
                    .append("machineId", new ObjectId())
                    .append("loggedAt", new Date());
            database.getCollection("audit_logs").insertOne(auditLog);

            // 12. Tags
            Document tag = new Document("workspaceId", workspaceId)
                    .append("tagName", "v1.0.0")
                    .append("description", "First stable release")
                    .append("commitId", new ObjectId())
                    .append("targetBranch", "main")
                    .append("createdBy", userId1)
                    .append("releaseNotes", "Initial launch")
                    .append("createdAt", new Date());
            database.getCollection("tags").insertOne(tag);

            // 13. Merge Requests
            Document mergeReq = new Document("workspaceId", workspaceId)
                    .append("title", "Implement dark mode")
                    .append("description", "Adds dual theme support")
                    .append("sourceBranch", "feature/dark-mode")
                    .append("targetBranch", "main")
                    .append("createdBy", userId1)
                    .append("reviewers", Arrays.asList(new ObjectId()))
                    .append("status", "open")
                    .append("labels", Arrays.asList("ui", "enhancement"))
                    .append("createdAt", new Date())
                    .append("updatedAt", new Date());
            database.getCollection("merge_requests").insertOne(mergeReq);

            // 14. Notifications
            Document notification = new Document("userId", userId1)
                    .append("type", "collaboration_request")
                    .append("title", "New collaboration request")
                    .append("message", "User sent you a request")
                    .append("isRead", false)
                    .append("relatedEntityId", new ObjectId())
                    .append("createdAt", new Date());
            database.getCollection("notifications").insertOne(notification);

            System.out.println("\n✅ Database seeding successfully completed! All 14 previous collections were cleared and rebuilt.");

        } catch (Exception e) {
            System.err.println("❌ Error connecting to MongoDB or executing seed.");
            e.printStackTrace();
        }
    }
}
