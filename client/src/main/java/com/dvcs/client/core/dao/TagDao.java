package com.dvcs.client.core.dao;

import com.dvcs.client.core.model.Tag;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;

public final class TagDao {

    private final MongoCollection<Document> tags;

    public TagDao(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.tags = database.getCollection("tags");
    }

    public void insert(Tag tag) {
        Objects.requireNonNull(tag, "tag");
        tags.insertOne(tag.toDocument());
    }

    public List<Tag> findByWorkspaceId(ObjectId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        List<Tag> result = new ArrayList<>();
        for (Document doc : tags.find(Filters.eq("workspaceId", workspaceId))) {
            result.add(Tag.fromDocument(doc));
        }
        return result;
    }

    public Optional<Tag> findByName(ObjectId workspaceId, String tagName) {
        Document doc = tags.find(Filters.and(
                Filters.eq("workspaceId", workspaceId),
                Filters.eq("tagName", tagName)
        )).first();
        return Optional.ofNullable(Tag.fromDocument(doc));
    }
}
