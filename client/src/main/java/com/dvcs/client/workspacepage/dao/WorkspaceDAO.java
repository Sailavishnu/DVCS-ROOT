package com.dvcs.client.workspacepage.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import com.mongodb.client.model.UpdateOptions;
import static com.mongodb.client.model.Updates.addToSet;
import static com.mongodb.client.model.Updates.pull;
import static com.mongodb.client.model.Updates.set;

public final class WorkspaceDAO {

    private final MongoCollection<Document> workspaces;
    private final MongoCollection<Document> folders;
    private final MongoCollection<Document> files;
    private final MongoCollection<Document> users;
    private final MongoCollection<Document> branches;
    private final MongoCollection<Document> tags;
    private final MongoCollection<Document> collaborationRequests;

    public WorkspaceDAO(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.workspaces = database.getCollection("workspaces");
        this.folders = database.getCollection("folders");
        this.files = database.getCollection("files");
        this.users = database.getCollection("users");
        this.branches = database.getCollection("branches");
        this.tags = database.getCollection("tags");
        this.collaborationRequests = database.getCollection("colab_requests");
    }

    public List<Document> findBranchesByWorkspaceId(ObjectId workspaceId) {
        return branches.find(eq("workspaceId", workspaceId)).into(new ArrayList<>());
    }

    public void insertBranch(Document branch) {
        Objects.requireNonNull(branch, "branch");
        branches.insertOne(branch);
    }

    public Optional<Document> findBranchByName(ObjectId workspaceId, String branchName) {
        return Optional.ofNullable(branches.find(and(eq("workspaceId", workspaceId), eq("branchName", branchName))).first());
    }

    public void markBranchAsDefault(ObjectId branchId) {
        branches.updateOne(
                new Document("_id", branchId),
                new Document("$set", new Document("isDefault", true)));
    }

    public List<Document> findTagsByWorkspaceId(ObjectId workspaceId) {
        return tags.find(eq("workspaceId", workspaceId)).into(new ArrayList<>());
    }

    public Optional<Document> findWorkspaceById(ObjectId workspaceId) {
        return Optional.ofNullable(workspaces.find(eq("_id", workspaceId)).first());
    }

    public List<Document> findFoldersByWorkspaceId(ObjectId workspaceId) {
        return folders.find(eq("workspaceId", workspaceId)).into(new ArrayList<>());
    }

    public Optional<Document> findFolderByWorkspaceIdAndName(ObjectId workspaceId, String folderName) {
        return Optional
                .ofNullable(folders.find(and(eq("workspaceId", workspaceId), eq("folderName", folderName))).first());
    }

    public ObjectId insertFolder(Document folderDoc) {
        folders.insertOne(folderDoc);
        return folderDoc.getObjectId("_id");
    }

    public List<Document> findFilesByFolderIds(List<ObjectId> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return List.of();
        }
        return files.find(in("folderId", folderIds)).into(new ArrayList<>());
    }

    public List<Document> findFilesByFolderIdsAndBranch(List<ObjectId> folderIds, ObjectId branchId, boolean includeUnbranched) {
        if (folderIds == null || folderIds.isEmpty()) return List.of();
        if (branchId == null) return findFilesByFolderIds(folderIds);
        Document query;
        if (includeUnbranched) {
            query = new Document("folderId", new Document("$in", folderIds))
                    .append("$or", List.of(
                            new Document("branchId", branchId),
                            new Document("branchId", null),
                            new Document("branchId", new Document("$exists", false))));
        } else {
            query = new Document("folderId", new Document("$in", folderIds))
                    .append("branchId", branchId);
        }
        return files.find(query).into(new ArrayList<>());
    }

    public List<Document> findUsersByIds(List<ObjectId> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return users.find(in("_id", userIds)).into(new ArrayList<>());
    }

    public List<Document> findAllUsers() {
        return users.find().into(new ArrayList<>());
    }

    public boolean updateWorkspaceName(ObjectId workspaceId, String newName) {
        return workspaces.updateOne(eq("_id", workspaceId), set("workspaceName", newName)).getModifiedCount() > 0;
    }

    public void updateWorkspaceConfig(ObjectId workspaceId, String visibility, int maxCollaborators) {
        workspaces.updateOne(
                eq("_id", workspaceId),
                new Document("$set", new Document("visibility", visibility)
                        .append("maxCollaborators", maxCollaborators)),
                new UpdateOptions().upsert(false));
    }

    public void addCollaborator(ObjectId workspaceId, ObjectId collaboratorId) {
        workspaces.updateOne(eq("_id", workspaceId), addToSet("collaborators", collaboratorId),
                new UpdateOptions().upsert(false));
    }

    public void removeCollaborator(ObjectId workspaceId, ObjectId collaboratorId) {
        workspaces.updateOne(eq("_id", workspaceId), pull("collaborators", collaboratorId));
    }

    public void deleteWorkspaceCascade(ObjectId workspaceId, List<ObjectId> folderIds, List<ObjectId> fileIds) {
        if (fileIds != null && !fileIds.isEmpty()) {
            files.deleteMany(in("_id", fileIds));
        }
        if (folderIds != null && !folderIds.isEmpty()) {
            folders.deleteMany(in("_id", folderIds));
        }
        workspaces.deleteOne(eq("_id", workspaceId));
    }

    public void createInvite(ObjectId requestedBy, ObjectId requestedTo, ObjectId workspaceId, String permission) {
        Objects.requireNonNull(requestedBy, "requestedBy");
        Objects.requireNonNull(requestedTo, "requestedTo");
        Objects.requireNonNull(workspaceId, "workspaceId");

        Document doc = new Document("_id", new ObjectId())
                .append("requestedBy", requestedBy)
                .append("requestedTo", requestedTo)
                .append("workspaceId", workspaceId)
                .append("fileId", null)
                .append("permission", permission == null || permission.isBlank() ? "write" : permission)
                .append("requestedAt", new java.util.Date())
                .append("status", "pending")
                .append("respondedAt", null);

        collaborationRequests.insertOne(doc);
    }

    public void updateCollaboratorPermission(ObjectId workspaceId, ObjectId userId, String permission) {
        collaborationRequests.updateOne(
            and(eq("workspaceId", workspaceId), eq("requestedTo", userId)),
            new Document("$set", new Document("permission", permission)));
    }

    public String getCollaboratorPermission(ObjectId workspaceId, ObjectId userId) {
        Document req = collaborationRequests.find(
            and(eq("workspaceId", workspaceId), eq("requestedTo", userId), eq("status", "accepted"))
        ).first();
        if (req == null) req = collaborationRequests.find(
            and(eq("workspaceId", workspaceId), eq("requestedTo", userId))
        ).first();
        if (req == null) return "write";
        String perm = req.getString("permission");
        return perm == null || perm.isBlank() ? "write" : perm;
    }
}
