package com.dvcs.client.core.model;

import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;

public record MergeRequest(
        ObjectId id,
        ObjectId workspaceId,
        String title,
        String description,
        String sourceBranch,
        String targetBranch,
        ObjectId createdBy,
        List<ObjectId> reviewers,
        List<String> labels,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public Document toDocument() {
        return new Document()
                .append("_id", id)
                .append("workspaceId", workspaceId)
                .append("title", title)
                .append("description", description)
                .append("sourceBranch", sourceBranch)
                .append("targetBranch", targetBranch)
                .append("createdBy", createdBy)
                .append("reviewers", reviewers)
                .append("labels", labels)
                .append("status", status)
                .append("createdAt", createdAt != null ? java.util.Date.from(createdAt) : null)
                .append("updatedAt", updatedAt != null ? java.util.Date.from(updatedAt) : null);
    }

    @SuppressWarnings("unchecked")
    public static MergeRequest fromDocument(Document doc) {
        if (doc == null) return null;
        return new MergeRequest(
                doc.getObjectId("_id"),
                doc.getObjectId("workspaceId"),
                doc.getString("title"),
                doc.getString("description"),
                doc.getString("sourceBranch"),
                doc.getString("targetBranch"),
                doc.getObjectId("createdBy"),
                (List<ObjectId>) doc.get("reviewers"),
                (List<String>) doc.get("labels"),
                doc.getString("status"),
                doc.getDate("createdAt") != null ? doc.getDate("createdAt").toInstant() : null,
                doc.getDate("updatedAt") != null ? doc.getDate("updatedAt").toInstant() : null
        );
    }
}
