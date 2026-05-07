package com.dvcs.client.core.model;

import java.time.Instant;
import org.bson.Document;
import org.bson.types.ObjectId;

public record Branch(
        ObjectId id,
        ObjectId workspaceId,
        String branchName,
        String description,
        ObjectId createdBy,
        boolean isDefault,
        boolean isProtected,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public Document toDocument() {
        return new Document()
                .append("_id", id)
                .append("workspaceId", workspaceId)
                .append("branchName", branchName)
                .append("description", description)
                .append("createdBy", createdBy)
                .append("isDefault", isDefault)
                .append("isProtected", isProtected)
                .append("status", status)
                .append("createdAt", createdAt != null ? java.util.Date.from(createdAt) : null)
                .append("updatedAt", updatedAt != null ? java.util.Date.from(updatedAt) : null);
    }

    public static Branch fromDocument(Document doc) {
        if (doc == null) return null;
        return new Branch(
                doc.getObjectId("_id"),
                doc.getObjectId("workspaceId"),
                doc.getString("branchName"),
                doc.getString("description"),
                doc.getObjectId("createdBy"),
                doc.getBoolean("isDefault", false),
                doc.getBoolean("isProtected", false),
                doc.getString("status"),
                doc.getDate("createdAt") != null ? doc.getDate("createdAt").toInstant() : null,
                doc.getDate("updatedAt") != null ? doc.getDate("updatedAt").toInstant() : null
        );
    }
}
