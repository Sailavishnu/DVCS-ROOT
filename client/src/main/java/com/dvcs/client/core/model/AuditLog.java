package com.dvcs.client.core.model;

import java.time.Instant;
import org.bson.Document;
import org.bson.types.ObjectId;

public record AuditLog(
        ObjectId id,
        ObjectId userId,
        String action,
        String entityAffected,
        String entityId,
        ObjectId machineId,
        Instant loggedAt) {

    public Document toDocument() {
        return new Document()
                .append("_id", id)
                .append("userId", userId)
                .append("action", action)
                .append("entityAffected", entityAffected)
                .append("entityId", entityId)
                .append("machineId", machineId)
                .append("loggedAt", loggedAt != null ? java.util.Date.from(loggedAt) : null);
    }

    public static AuditLog fromDocument(Document doc) {
        if (doc == null) return null;
        return new AuditLog(
                doc.getObjectId("_id"),
                doc.getObjectId("userId"),
                doc.getString("action"),
                doc.getString("entityAffected"),
                doc.getString("entityId"),
                doc.getObjectId("machineId"),
                doc.getDate("loggedAt") != null ? doc.getDate("loggedAt").toInstant() : null
        );
    }
}
