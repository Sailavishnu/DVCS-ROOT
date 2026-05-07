package com.dvcs.client.workspacepage.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

public final class CommentDAO {

    private final MongoCollection<Document> workspaceComments;
    private final MongoCollection<Document> fileComments;

    public CommentDAO(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.workspaceComments = database.getCollection("workspace_comments");
        this.fileComments = database.getCollection("file_comments");
    }

    public List<Document> findWorkspaceComments(ObjectId workspaceId) {
        return workspaceComments.find(eq("workspaceId", workspaceId))
                .sort(new Document("createdAt", -1))
                .into(new ArrayList<>());
    }

    public List<Document> findFileComments(ObjectId workspaceId) {
        return fileComments.find(eq("workspaceId", workspaceId))
                .sort(new Document("createdAt", -1))
                .into(new ArrayList<>());
    }

    public void insertWorkspaceComment(ObjectId workspaceId, ObjectId authorId, String message, Date createdAt) {
        workspaceComments.insertOne(new Document("_id", new ObjectId())
                .append("workspaceId", workspaceId)
                .append("authorId", authorId)
                .append("content", message)
                .append("createdAt", createdAt)
                .append("updatedAt", createdAt));
    }

    public void insertFileComment(ObjectId workspaceId, ObjectId fileId, ObjectId authorId, String message, Date createdAt) {
        fileComments.insertOne(new Document("_id", new ObjectId())
                .append("workspaceId", workspaceId)
                .append("fileId", fileId)
                .append("authorId", authorId)
                .append("content", message)
                .append("createdAt", createdAt)
                .append("updatedAt", createdAt));
    }

    public void deleteWorkspaceComments(ObjectId workspaceId) {
        workspaceComments.deleteMany(eq("workspaceId", workspaceId));
        fileComments.deleteMany(eq("workspaceId", workspaceId));
    }

    public void deleteFileComments(ObjectId fileId) {
        fileComments.deleteMany(eq("fileId", fileId));
    }

    public void deleteFileComments(List<ObjectId> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        fileComments.deleteMany(in("fileId", fileIds));
    }
}
