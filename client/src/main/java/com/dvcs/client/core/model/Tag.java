package com.dvcs.client.core.model;

import java.time.Instant;
import org.bson.Document;
import org.bson.types.ObjectId;

public record Tag(
        ObjectId id,
        ObjectId workspaceId,
        String tagName,
        String description,
        ObjectId commitId,
        String targetBranch,
        ObjectId createdBy,
        String releaseNotes,
        Instant createdAt) {

    public Document toDocument() {
        return new Document()
                .append("_id", id)
                .append("workspaceId", workspaceId)
                .append("tagName", tagName)
                .append("description", description)
                .append("commitId", commitId)
                .append("targetBranch", targetBranch)
                .append("createdBy", createdBy)
                .append("releaseNotes", releaseNotes)
                .append("createdAt", createdAt != null ? java.util.Date.from(createdAt) : null);
    }

    public static Tag fromDocument(Document doc) {
        if (doc == null) return null;
        return new Tag(
                doc.getObjectId("_id"),
                doc.getObjectId("workspaceId"),
                doc.getString("tagName"),
                doc.getString("description"),
                doc.getObjectId("commitId"),
                doc.getString("targetBranch"),
                doc.getObjectId("createdBy"),
                doc.getString("releaseNotes"),
                doc.getDate("createdAt") != null ? doc.getDate("createdAt").toInstant() : null
        );
    }
}
