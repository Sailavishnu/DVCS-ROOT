package com.dvcs.client.core.model;

import java.time.Instant;
import org.bson.Document;
import org.bson.types.ObjectId;

public record WorkspaceMachine(
        ObjectId id,
        ObjectId workspaceId,
        String machineName,
        String ipAddress,
        Instant registeredAt,
        Instant lastAccessedAt) {

    public Document toDocument() {
        return new Document()
                .append("_id", id)
                .append("workspaceId", workspaceId)
                .append("machineName", machineName)
                .append("ipAddress", ipAddress)
                .append("registeredAt", registeredAt != null ? java.util.Date.from(registeredAt) : null)
                .append("lastAccessedAt", lastAccessedAt != null ? java.util.Date.from(lastAccessedAt) : null);
    }

    public static WorkspaceMachine fromDocument(Document doc) {
        if (doc == null) return null;
        return new WorkspaceMachine(
                doc.getObjectId("_id"),
                doc.getObjectId("workspaceId"),
                doc.getString("machineName"),
                doc.getString("ipAddress"),
                doc.getDate("registeredAt") != null ? doc.getDate("registeredAt").toInstant() : null,
                doc.getDate("lastAccessedAt") != null ? doc.getDate("lastAccessedAt").toInstant() : null
        );
    }
}
