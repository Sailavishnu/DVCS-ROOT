package com.dvcs.client.core.dao;

import com.dvcs.client.core.model.FolderPermission;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bson.Document;
import org.bson.types.ObjectId;

public final class FolderPermissionDao {

    private final MongoCollection<Document> permissions;

    public FolderPermissionDao(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.permissions = database.getCollection("folder_permissions");
    }

    public void insert(FolderPermission permission) {
        Objects.requireNonNull(permission, "permission");
        permissions.insertOne(permission.toDocument());
    }

    public List<FolderPermission> findByFolderId(ObjectId folderId) {
        Objects.requireNonNull(folderId, "folderId");
        List<FolderPermission> result = new ArrayList<>();
        for (Document doc : permissions.find(Filters.eq("folderId", folderId))) {
            result.add(FolderPermission.fromDocument(doc));
        }
        return result;
    }

    public List<FolderPermission> findByUserId(ObjectId userId) {
        Objects.requireNonNull(userId, "userId");
        List<FolderPermission> result = new ArrayList<>();
        for (Document doc : permissions.find(Filters.eq("userId", userId))) {
            result.add(FolderPermission.fromDocument(doc));
        }
        return result;
    }
}
