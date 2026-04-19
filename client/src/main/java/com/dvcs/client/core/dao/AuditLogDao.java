package com.dvcs.client.core.dao;

import com.dvcs.client.core.model.AuditLog;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bson.Document;
import org.bson.types.ObjectId;

public final class AuditLogDao {

    private final MongoCollection<Document> logs;

    public AuditLogDao(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.logs = database.getCollection("audit_logs");
    }

    public void insert(AuditLog log) {
        Objects.requireNonNull(log, "log");
        logs.insertOne(log.toDocument());
    }

    public List<AuditLog> findRecentLogs(int limit) {
        List<AuditLog> result = new ArrayList<>();
        for (Document doc : logs.find().sort(Sorts.descending("loggedAt")).limit(limit)) {
            result.add(AuditLog.fromDocument(doc));
        }
        return result;
    }

    public List<AuditLog> findByUserId(ObjectId userId) {
        Objects.requireNonNull(userId, "userId");
        List<AuditLog> result = new ArrayList<>();
        for (Document doc : logs.find(Filters.eq("userId", userId)).sort(Sorts.descending("loggedAt"))) {
            result.add(AuditLog.fromDocument(doc));
        }
        return result;
    }
}
