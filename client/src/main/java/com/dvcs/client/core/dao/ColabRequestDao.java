package com.dvcs.client.core.dao;

import com.dvcs.client.core.model.ColabRequest;
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

public final class ColabRequestDao {

    private final MongoCollection<Document> requests;

    public ColabRequestDao(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        // Using 'colab_requests' as defined in mongo.txt and DatabaseSeeder
        this.requests = database.getCollection("colab_requests");
    }

    public void insert(ColabRequest request) {
        Objects.requireNonNull(request, "request");
        requests.insertOne(request.toDocument());
    }

    public List<ColabRequest> findPendingForUser(ObjectId userId) {
        Objects.requireNonNull(userId, "userId");
        List<ColabRequest> result = new ArrayList<>();
        for (Document doc : requests.find(Filters.and(
                Filters.eq("requestedTo", userId),
                Filters.eq("status", "pending")
        ))) {
            result.add(ColabRequest.fromDocument(doc));
        }
        return result;
    }

    public Optional<ColabRequest> findById(ObjectId id) {
        Document doc = requests.find(Filters.eq("_id", id)).first();
        return Optional.ofNullable(ColabRequest.fromDocument(doc));
    }

    public boolean updateStatus(ObjectId requestId, String status) {
        return requests.updateOne(
                Filters.eq("_id", requestId),
                Updates.combine(
                        Updates.set("status", status),
                        Updates.set("respondedAt", new java.util.Date())
                )
        ).getModifiedCount() > 0;
    }
}
