package com.dvcs.client.core.model;

import java.time.Instant;
import org.bson.Document;
import org.bson.types.ObjectId;

public record FolderPermission(
        ObjectId id,
        ObjectId folderId,
        ObjectId userId,
        String permissionType,
        ObjectId grantedBy,
        Instant grantedAt) {

    public Document toDocument() {
        return new Document()
                .append("_id", id)
                .append("folderId", folderId)
                .append("userId", userId)
                .append("permissionType", permissionType)
                .append("grantedBy", grantedBy)
                .append("grantedAt", grantedAt != null ? java.util.Date.from(grantedAt) : null);
    }

    public static FolderPermission fromDocument(Document doc) {
        if (doc == null) return null;
        return new FolderPermission(
                doc.getObjectId("_id"),
                doc.getObjectId("folderId"),
                doc.getObjectId("userId"),
                doc.getString("permissionType"),
                doc.getObjectId("grantedBy"),
                doc.getDate("grantedAt") != null ? doc.getDate("grantedAt").toInstant() : null
        );
    }
}
