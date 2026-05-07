package com.dvcs.client.dashboard.data.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;

import com.dvcs.client.core.model.ColabRequest;

public final class CollaborationRequestDao {

    private final MongoCollection<Document> collaborationRequests;

    public CollaborationRequestDao(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.collaborationRequests = database.getCollection("colab_requests");
        ensureIndexes();
    }

    private void ensureIndexes() {
        collaborationRequests.createIndex(Indexes.ascending("requestedTo", "status"));
        collaborationRequests.createIndex(Indexes.ascending("fileId"));
    }

    public List<ColabRequest> findPendingForUser(ObjectId requestedTo) {
        Objects.requireNonNull(requestedTo, "requestedTo");
        List<ColabRequest> result = new ArrayList<>();
        for (Document doc : collaborationRequests.find(and(eq("requestedTo", requestedTo), eq("status", "pending")))) {
            result.add(ColabRequest.fromDocument(doc));
        }
        return result;
    }

    public Optional<ColabRequest> findById(ObjectId requestId) {
        Objects.requireNonNull(requestId, "requestId");
        Document doc = collaborationRequests.find(eq("_id", requestId)).first();
        return doc == null ? Optional.empty() : Optional.of(ColabRequest.fromDocument(doc));
    }

    public List<ColabRequest> findByRequestedToAndStatus(ObjectId requestedTo, String status) {
        Objects.requireNonNull(requestedTo, "requestedTo");
        Objects.requireNonNull(status, "status");
        List<ColabRequest> result = new ArrayList<>();
        for (Document doc : collaborationRequests.find(and(eq("requestedTo", requestedTo), eq("status", status)))) {
            result.add(ColabRequest.fromDocument(doc));
        }
        return result;
    }

    public List<ColabRequest> findByFileIdsAndStatus(List<ObjectId> fileIds, String status) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        List<ColabRequest> result = new ArrayList<>();
        for (Document doc : collaborationRequests.find(and(new Document("fileId", new Document("$in", fileIds)), eq("status", status)))) {
            result.add(ColabRequest.fromDocument(doc));
        }
        return result;
    }

    public List<ColabRequest> findAcceptedBothDirections(ObjectId userId) {
        Objects.requireNonNull(userId, "userId");
        List<ColabRequest> result = new ArrayList<>();
        Document filter = new Document("$and", List.of(
                new Document("status", "accepted"),
                new Document("$or", List.of(
                        new Document("requestedBy", userId),
                        new Document("requestedTo", userId)))));
        for (Document doc : collaborationRequests.find(filter)) {
            result.add(ColabRequest.fromDocument(doc));
        }
        return result;
    }

    public void createInvite(ObjectId requestedBy, ObjectId requestedTo, ObjectId workspaceId) {
        Objects.requireNonNull(requestedBy, "requestedBy");
        Objects.requireNonNull(requestedTo, "requestedTo");
        Document doc = new Document("_id", new org.bson.types.ObjectId())
                .append("requestedBy", requestedBy)
                .append("requestedTo", requestedTo)
                .append("workspaceId", workspaceId)
                .append("fileId", null)
                .append("requestedAt", new Date())
                .append("status", "pending")
                .append("respondedAt", null);
        collaborationRequests.insertOne(doc);
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
