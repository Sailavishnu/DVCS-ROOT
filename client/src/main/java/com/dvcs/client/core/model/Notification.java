package com.dvcs.client.core.model;

import java.time.Instant;
import org.bson.Document;
import org.bson.types.ObjectId;

public record Notification(
        ObjectId id,
        ObjectId userId,
        String type,
        String title,
        String message,
        boolean isRead,
        ObjectId relatedEntityId,
        Instant createdAt) {

    public Document toDocument() {
        return new Document()
                .append("_id", id)
                .append("userId", userId)
                .append("type", type)
                .append("title", title)
                .append("message", message)
                .append("isRead", isRead)
                .append("relatedEntityId", relatedEntityId)
                .append("createdAt", createdAt != null ? java.util.Date.from(createdAt) : null);
    }

    public static Notification fromDocument(Document doc) {
        if (doc == null) return null;
        return new Notification(
                doc.getObjectId("_id"),
                doc.getObjectId("userId"),
                doc.getString("type"),
                doc.getString("title"),
                doc.getString("message"),
                doc.getBoolean("isRead", false),
                doc.getObjectId("relatedEntityId"),
                doc.getDate("createdAt") != null ? doc.getDate("createdAt").toInstant() : null
        );
    }
}
