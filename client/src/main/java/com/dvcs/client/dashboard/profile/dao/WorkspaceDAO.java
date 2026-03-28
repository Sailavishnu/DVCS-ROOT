package com.dvcs.client.dashboard.profile.dao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public final class WorkspaceDAO {

    private final MongoCollection<Document> workspaces;
    private final MongoCollection<Document> folders;
    private final MongoCollection<Document> files;

    public WorkspaceDAO(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.workspaces = database.getCollection("workspaces");
        this.folders = database.getCollection("folders");
        this.files = database.getCollection("files");
    }

    public List<ObjectId> findWorkspaceIdsByCreator(ObjectId userId) {
        Objects.requireNonNull(userId, "userId");
        List<Document> docs = workspaces.find(new Document("createdBy", userId)).into(new ArrayList<>());
        List<ObjectId> ids = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            ObjectId id = doc.getObjectId("_id");
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    public int countFoldersByWorkspaceIds(List<ObjectId> workspaceIds) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return 0;
        }
        return (int) folders.countDocuments(new Document("workspaceId", new Document("$in", workspaceIds)));
    }

    public int countFilesByWorkspaceIds(List<ObjectId> workspaceIds) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return 0;
        }

        List<Document> folderDocs = folders.find(new Document("workspaceId", new Document("$in", workspaceIds)))
                .into(new ArrayList<>());
        List<ObjectId> folderIds = new ArrayList<>(folderDocs.size());
        for (Document folderDoc : folderDocs) {
            ObjectId id = folderDoc.getObjectId("_id");
            if (id != null) {
                folderIds.add(id);
            }
        }

        if (folderIds.isEmpty()) {
            return 0;
        }
        return (int) files.countDocuments(new Document("folderId", new Document("$in", folderIds)));
    }

    public Map<ObjectId, String> findWorkspaceNamesByIds(List<ObjectId> workspaceIds) {
        Map<ObjectId, String> names = new LinkedHashMap<>();
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return names;
        }

        List<Document> docs = workspaces.find(new Document("_id", new Document("$in", workspaceIds)))
                .into(new ArrayList<>());
        for (Document doc : docs) {
            ObjectId id = doc.getObjectId("_id");
            String name = doc.getString("workspaceName");
            if (id != null) {
                names.put(id, name == null || name.isBlank() ? "Workspace" : name);
            }
        }
        return names;
    }
}
