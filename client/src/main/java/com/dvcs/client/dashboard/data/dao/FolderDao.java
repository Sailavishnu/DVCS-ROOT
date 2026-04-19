package com.dvcs.client.dashboard.data.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.Indexes;

public final class FolderDao {

    private final MongoCollection<Document> folders;

    public FolderDao(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.folders = database.getCollection("folders");
        ensureIndexes();
    }

    private void ensureIndexes() {
        folders.createIndex(Indexes.ascending("workspaceId"));
        folders.createIndex(Indexes.ascending("folderName"));
    }

    public ObjectId insert(Document folderDocument) {
        Objects.requireNonNull(folderDocument, "folderDocument");
        folders.insertOne(folderDocument);
        return folderDocument.getObjectId("_id");
    }

    public List<Document> findByWorkspaceId(ObjectId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        return folders.find(eq("workspaceId", workspaceId)).into(new ArrayList<>());
    }

    public List<Document> findByWorkspaceIds(List<ObjectId> workspaceIds) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return List.of();
        }
        return folders.find(new Document("workspaceId", new Document("$in", workspaceIds))).into(new ArrayList<>());
    }

    public long countByWorkspaceIds(List<ObjectId> workspaceIds) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return 0;
        }
        return folders.countDocuments(new Document("workspaceId", new Document("$in", workspaceIds)));
    }

    public List<Document> searchByFolderName(String query) {
        Objects.requireNonNull(query, "query");
        Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
        return folders.find(new Document("folderName", pattern)).into(new ArrayList<>());
    }

    public Document findByWorkspaceIdAndName(ObjectId workspaceId, String folderName) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(folderName, "folderName");
        return folders.find(new Document("workspaceId", workspaceId).append("folderName", folderName)).first();
    }

    public List<Document> findByIds(List<ObjectId> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return List.of();
        }
        return folders.find(new Document("_id", new Document("$in", folderIds))).into(new ArrayList<>());
    }
}
