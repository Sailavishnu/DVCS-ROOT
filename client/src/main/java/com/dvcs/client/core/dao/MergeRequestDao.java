package com.dvcs.client.core.dao;

import com.dvcs.client.core.model.MergeRequest;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;

public final class MergeRequestDao {

    private final MongoCollection<Document> mergeRequests;

    public MergeRequestDao(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.mergeRequests = database.getCollection("merge_requests");
    }

    public void insert(MergeRequest mr) {
        Objects.requireNonNull(mr, "mergeRequest");
        mergeRequests.insertOne(mr.toDocument());
    }

    public List<MergeRequest> findByWorkspaceId(ObjectId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        List<MergeRequest> result = new ArrayList<>();
        for (Document doc : mergeRequests.find(Filters.eq("workspaceId", workspaceId))) {
            result.add(MergeRequest.fromDocument(doc));
        }
        return result;
    }

    public Optional<MergeRequest> findById(ObjectId id) {
        Document doc = mergeRequests.find(Filters.eq("_id", id)).first();
        return Optional.ofNullable(MergeRequest.fromDocument(doc));
    }

    public boolean updateStatus(ObjectId id, String status) {
        return mergeRequests.updateOne(
                Filters.eq("_id", id),
                Updates.combine(
                        Updates.set("status", status),
                        Updates.set("updatedAt", new java.util.Date())
                )
        ).getModifiedCount() > 0;
    }
}
