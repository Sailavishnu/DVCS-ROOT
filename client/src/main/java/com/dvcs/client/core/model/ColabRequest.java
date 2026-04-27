package com.dvcs.client.core.model;

import java.time.Instant;
import org.bson.Document;
import org.bson.types.ObjectId;

public record ColabRequest(
        ObjectId id,
        ObjectId fileId,
        ObjectId workspaceId,
        ObjectId requestedBy,
        ObjectId requestedTo,
        Instant requestedAt,
        String status,
        Instant respondedAt) {

    public Document toDocument() {
        return new Document()
                .append("_id", id)
                .append("fileId", fileId)
                .append("workspaceId", workspaceId)
                .append("requestedBy", requestedBy)
                .append("requestedTo", requestedTo)
                .append("requestedAt", requestedAt != null ? java.util.Date.from(requestedAt) : null)
                .append("status", status)
                .append("respondedAt", respondedAt != null ? java.util.Date.from(respondedAt) : null);
    }

    public static ColabRequest fromDocument(Document doc) {
        if (doc == null) return null;
        return new ColabRequest(
                doc.getObjectId("_id"),
                doc.getObjectId("fileId"),
                doc.getObjectId("workspaceId"),
                doc.getObjectId("requestedBy"),
                doc.getObjectId("requestedTo"),
                doc.getDate("requestedAt") != null ? doc.getDate("requestedAt").toInstant() : null,
                doc.getString("status"),
                doc.getDate("respondedAt") != null ? doc.getDate("respondedAt").toInstant() : null
        );
    }
}
