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

    public WorkspaceDAO(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.workspaces = database.getCollection("workspaces");
        this.folders = database.getCollection("folders");
        this.files = database.getCollection("files");
        this.users = database.getCollection("users");
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
}
