package com.dvcs.client.dashboard.data.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.Indexes;

public final class WorkspaceDao {

    private final MongoCollection<Document> workspaces;

    public WorkspaceDao(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.workspaces = database.getCollection("workspaces");
        ensureIndexes();
    }

    private void ensureIndexes() {
        workspaces.createIndex(Indexes.ascending("workspaceName"));
        workspaces.createIndex(Indexes.ascending("createdBy"));
    }

    public ObjectId insert(Document workspaceDocument) {
        Objects.requireNonNull(workspaceDocument, "workspaceDocument");
        workspaces.insertOne(workspaceDocument);
        return workspaceDocument.getObjectId("_id");
    }

    public List<Document> findByCreator(ObjectId createdBy) {
        Objects.requireNonNull(createdBy, "createdBy");
        return workspaces.find(eq("createdBy", createdBy)).into(new ArrayList<>());
    }

    public Optional<Document> findById(ObjectId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        return Optional.ofNullable(workspaces.find(eq("_id", workspaceId)).first());
    }

    public List<Document> searchByWorkspaceName(String query) {
        Objects.requireNonNull(query, "query");
        Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
        return workspaces.find(new Document("workspaceName", pattern)).into(new ArrayList<>());
    }

    public List<Document> findByIds(List<ObjectId> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return workspaces.find(new Document("_id", new Document("$in", ids))).into(new ArrayList<>());
    }
}
