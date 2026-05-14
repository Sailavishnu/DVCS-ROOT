package com.dvcs.client.dashboard.profile.dao;

import java.util.Objects;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.ne;
import static com.mongodb.client.model.Filters.regex;

public final class UserDAO {

    private final MongoCollection<Document> users;

    public UserDAO(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.users = database.getCollection("users");
    }

    public Optional<Document> findById(ObjectId userId) {
        Objects.requireNonNull(userId, "userId");
        return Optional.ofNullable(users.find(eq("_id", userId)).first());
    }

    public boolean existsByUsername(String username) {
        Objects.requireNonNull(username, "username");
        return users.find(eq("username", username.trim())).first() != null;
    }

    public boolean updateProfile(ObjectId userId, String name, String username, String passwordHashOrNull, String bio) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(username, "username");

        Document updateFields = new Document("name", name)
                .append("username", username.trim());
        if (passwordHashOrNull != null && !passwordHashOrNull.isBlank()) {
            updateFields.append("passwordHash", passwordHashOrNull);
        }
        if (bio != null) {
            updateFields.append("bio", bio);
        }

        return users.updateOne(eq("_id", userId), new Document("$set", updateFields)).getModifiedCount() > 0;
    }

    public Optional<Document> findByUsername(String username) {
        Objects.requireNonNull(username, "username");
        String trimmed = username.trim();
        // Try exact match first, then case-insensitive
        Document exact = users.find(eq("username", trimmed)).first();
        if (exact != null) return Optional.of(exact);
        return Optional.ofNullable(users.find(regex("username", "^" + java.util.regex.Pattern.quote(trimmed) + "$", "i")).first());
    }

    public boolean isUsernameUsedByAnother(ObjectId userId, String username) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(username, "username");
        return users.find(and(eq("username", username.trim()), ne("_id", userId))).first() != null;
    }
}
