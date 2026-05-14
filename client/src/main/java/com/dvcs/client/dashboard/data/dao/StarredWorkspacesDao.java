package com.dvcs.client.dashboard.data.dao;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bson.Document;
import org.bson.types.ObjectId;

public final class StarredWorkspacesDao {

    private final MongoCollection<Document> collection;

    public StarredWorkspacesDao(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.collection = database.getCollection("starred_workspaces");
    }

    public boolean isStarred(ObjectId userId, ObjectId workspaceId) {
        return collection.find(and(eq("userId", userId), eq("workspaceId", workspaceId))).first() != null;
    }

    public void star(ObjectId userId, ObjectId workspaceId) {
        if (!isStarred(userId, workspaceId)) {
            collection.insertOne(new Document("userId", userId)
                    .append("workspaceId", workspaceId)
                    .append("starredAt", new java.util.Date()));
        }
    }

    public void unstar(ObjectId userId, ObjectId workspaceId) {
        collection.deleteOne(and(eq("userId", userId), eq("workspaceId", workspaceId)));
    }

    public List<ObjectId> findStarredWorkspaceIds(ObjectId userId) {
        List<ObjectId> ids = new ArrayList<>();
        for (Document doc : collection.find(eq("userId", userId))) {
            ObjectId wsId = doc.getObjectId("workspaceId");
            if (wsId != null) ids.add(wsId);
        }
        return ids;
    }
}
