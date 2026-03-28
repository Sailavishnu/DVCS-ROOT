package com.dvcs.client.dashboard.profile.dao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.dvcs.client.dashboard.profile.ProfileService;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public final class CommitDAO {

    private final MongoCollection<Document> commits;

    public CommitDAO(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.commits = database.getCollection("commits");
    }

    public int countCommitsByWorkspaceIds(List<ObjectId> workspaceIds) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return 0;
        }
        return (int) commits.countDocuments(new Document("workspaceId", new Document("$in", workspaceIds)));
    }

    public ProfileService.PopularWorkspace findPopularWorkspace(List<ObjectId> workspaceIds,
            Map<ObjectId, String> workspaceNames) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return new ProfileService.PopularWorkspace("No workspace yet", 0);
        }

        List<Document> pipeline = List.of(
                new Document("$match", new Document("workspaceId", new Document("$in", workspaceIds))),
                new Document("$group", new Document("_id", "$workspaceId").append("count", new Document("$sum", 1))),
                new Document("$sort", new Document("count", -1)),
                new Document("$limit", 1));

        Document top = commits.aggregate(pipeline).first();
        if (top == null) {
            String fallback = workspaceNames.values().stream().findFirst().orElse("No workspace yet");
            return new ProfileService.PopularWorkspace(fallback, 0);
        }

        ObjectId workspaceId = top.getObjectId("_id");
        int count = top.getInteger("count", 0);
        String name = workspaceNames.getOrDefault(workspaceId, "Workspace");
        return new ProfileService.PopularWorkspace(name, count);
    }

    public List<ProfileService.PopularWorkspace> findTopPopularWorkspaces(
            List<ObjectId> workspaceIds,
            Map<ObjectId, String> workspaceNames,
            int limit) {
        List<ProfileService.PopularWorkspace> result = new ArrayList<>();
        if (workspaceIds == null || workspaceIds.isEmpty() || limit <= 0) {
            return result;
        }

        List<Document> pipeline = List.of(
                new Document("$match", new Document("workspaceId", new Document("$in", workspaceIds))),
                new Document("$group", new Document("_id", "$workspaceId").append("count", new Document("$sum", 1))),
                new Document("$sort", new Document("count", -1)),
                new Document("$limit", limit));

        List<Document> docs = commits.aggregate(pipeline).into(new ArrayList<>());
        for (Document doc : docs) {
            ObjectId workspaceId = doc.getObjectId("_id");
            int count = doc.getInteger("count", 0);
            String name = workspaceNames.getOrDefault(workspaceId, "Workspace");
            result.add(new ProfileService.PopularWorkspace(name, count));
        }

        return result;
    }

    public Map<LocalDate, Integer> loadLast30DayCommitCounts(ObjectId userId, List<ObjectId> workspaceIds) {
        Objects.requireNonNull(userId, "userId");

        Map<LocalDate, Integer> counts = new LinkedHashMap<>();
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return counts;
        }

        Instant from = LocalDate.now().minusDays(29)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        Document dateMatch = new Document("$gte", java.util.Date.from(from));

        // Mongo doesn't support parallel custom keys like $orDate, so build full match
        // doc.
        Document fullMatch = new Document("workspaceId", new Document("$in", workspaceIds))
                .append("$and", List.of(
                        new Document("$or", List.of(
                                new Document("authorId", userId),
                                new Document("userId", userId),
                                new Document("createdBy", userId))),
                        new Document("$or", List.of(
                                new Document("createdAt", dateMatch),
                                new Document("committedAt", dateMatch),
                                new Document("timestamp", dateMatch),
                                new Document("date", dateMatch)))));

        List<Document> docs = commits.find(fullMatch).into(new ArrayList<>());
        for (Document doc : docs) {
            LocalDate date = extractDate(doc);
            if (date != null) {
                counts.put(date, counts.getOrDefault(date, 0) + 1);
            }
        }
        return counts;
    }

    private static LocalDate extractDate(Document doc) {
        java.util.Date date = doc.getDate("createdAt");
        if (date == null) {
            date = doc.getDate("committedAt");
        }
        if (date == null) {
            date = doc.getDate("timestamp");
        }
        if (date == null) {
            date = doc.getDate("date");
        }
        if (date == null) {
            return null;
        }
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
