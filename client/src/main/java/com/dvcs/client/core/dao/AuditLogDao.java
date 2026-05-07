package com.dvcs.client.core.dao;

import com.dvcs.client.core.model.AuditLog;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public Map<LocalDate, Integer> loadDailyActionCounts(ObjectId userId, int days) {
        Objects.requireNonNull(userId, "userId");

        Map<LocalDate, Integer> counts = new LinkedHashMap<>();
        if (days <= 0) {
            return counts;
        }

        Instant from = LocalDate.now()
                .minusDays(days - 1L)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        Document query = new Document("userId", userId)
                .append("loggedAt", new Document("$gte", java.util.Date.from(from)));

        for (Document doc : logs.find(query).sort(Sorts.ascending("loggedAt"))) {
            java.util.Date loggedAt = doc.getDate("loggedAt");
            if (loggedAt == null) {
                continue;
            }

            LocalDate date = loggedAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            counts.put(date, counts.getOrDefault(date, 0) + 1);
        }
        return counts;
    }
}
