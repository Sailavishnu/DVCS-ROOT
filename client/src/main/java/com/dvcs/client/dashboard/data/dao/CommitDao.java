package com.dvcs.client.dashboard.data.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public final class CommitDao {

    private final MongoCollection<Document> commits;

    public CommitDao(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.commits = database.getCollection("commits");
    }

    public List<Document> searchByMessage(String query, List<ObjectId> allowedFileIds) {
        Objects.requireNonNull(query, "query");
        if (allowedFileIds == null || allowedFileIds.isEmpty()) {
            return List.of();
        }
        Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
        return commits.find(
                new Document("message", pattern)
                        .append("fileId", new Document("$in", allowedFileIds)))
                .sort(new Document("committedAt", -1))
                .limit(50)
                .into(new ArrayList<>());
    }

    public List<Document> findRecentByWorkspaceIds(List<ObjectId> workspaceIds, int limit) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return List.of();
        }
        return commits.find(new Document("workspaceId", new Document("$in", workspaceIds)))
                .sort(new Document("committedAt", -1))
                .limit(limit)
                .into(new ArrayList<>());
    }

    public List<Document> findLatestByFileIds(List<ObjectId> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        return commits.find(new Document("fileId", new Document("$in", fileIds)))
                .sort(new Document("committedAt", -1))
                .into(new ArrayList<>());
    }
}
