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

    public boolean updateProfile(ObjectId userId, String name, String username, String passwordHashOrNull) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(username, "username");

        Document updateFields = new Document("name", name)
                .append("username", username.trim());
        if (passwordHashOrNull != null && !passwordHashOrNull.isBlank()) {
            updateFields.append("passwordHash", passwordHashOrNull);
        }

        return users.updateOne(eq("_id", userId), new Document("$set", updateFields)).getModifiedCount() > 0;
    }

    public boolean isUsernameUsedByAnother(ObjectId userId, String username) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(username, "username");
        return users.find(and(eq("username", username.trim()), ne("_id", userId))).first() != null;
    }
}
