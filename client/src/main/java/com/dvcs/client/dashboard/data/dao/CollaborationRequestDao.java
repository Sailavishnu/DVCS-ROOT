package com.dvcs.client.dashboard.data.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;

public final class CollaborationRequestDao {

    private final MongoCollection<Document> collaborationRequests;

    public CollaborationRequestDao(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.collaborationRequests = database.getCollection("collaboration_requests");
        ensureIndexes();
    }

    private void ensureIndexes() {
        collaborationRequests.createIndex(Indexes.ascending("requestedTo", "status"));
        collaborationRequests.createIndex(Indexes.ascending("fileId"));
    }

    public List<Document> findPendingForUser(ObjectId requestedTo) {
        Objects.requireNonNull(requestedTo, "requestedTo");
        return collaborationRequests
                .find(and(eq("requestedTo", requestedTo), eq("status", "pending")))
                .into(new ArrayList<>());
    }

    public List<Document> findByRequestedToAndStatus(ObjectId requestedTo, String status) {
        Objects.requireNonNull(requestedTo, "requestedTo");
        Objects.requireNonNull(status, "status");
        return collaborationRequests
                .find(and(eq("requestedTo", requestedTo), eq("status", status)))
                .into(new ArrayList<>());
    }

    public List<Document> findByFileIdsAndStatus(List<ObjectId> fileIds, String status) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        return collaborationRequests
                .find(and(new Document("fileId", new Document("$in", fileIds)), eq("status", status)))
                .into(new ArrayList<>());
    }

    public boolean updateStatus(ObjectId requestId, String status) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(status, "status");
        return collaborationRequests
                .updateOne(eq("_id", requestId), Updates.combine(
                        Updates.set("status", status),
                        Updates.set("respondedAt", new Date())))
                .getModifiedCount() > 0;
    }
}
