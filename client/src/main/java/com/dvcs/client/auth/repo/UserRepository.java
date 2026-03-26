package com.dvcs.client.auth.repo;

import com.dvcs.client.auth.model.User;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import java.util.Objects;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;

public final class UserRepository {

    private final MongoCollection<Document> users;

    public UserRepository(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.users = database.getCollection("users");
        ensureIndexes();
    }

    private void ensureIndexes() {
        users.createIndex(Indexes.ascending("username"), new IndexOptions().unique(true));
    }

    public Optional<User> findByUsername(String username) {
        Objects.requireNonNull(username, "username");
        Document doc = users.find(eq("username", username)).first();
        return Optional.ofNullable(User.fromDocument(doc));
    }

    public Optional<User> findById(ObjectId id) {
        Objects.requireNonNull(id, "id");
        Document doc = users.find(eq("_id", id)).first();
        return Optional.ofNullable(User.fromDocument(doc));
    }

    public InsertResult insert(User user) {
        Objects.requireNonNull(user, "user");

        try {
            users.insertOne(user.toDocument());
            return InsertResult.ok();
        } catch (MongoWriteException e) {
            if (e.getError() != null && e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                return InsertResult.duplicateUsernameError();
            }
            return InsertResult.failure(e.getMessage());
        }
    }

    public boolean updateNameByUsername(String username, String newName) {
        Objects.requireNonNull(username, "username");
        return users.updateOne(eq("username", username), Updates.set("name", newName)).getModifiedCount() > 0;
    }

    public boolean updateNameById(ObjectId id, String newName) {
        Objects.requireNonNull(id, "id");
        return users.updateOne(eq("_id", id), Updates.set("name", newName)).getModifiedCount() > 0;
    }

    public record InsertResult(boolean success, boolean duplicateUsername, String errorMessage) {
        public static InsertResult ok() {
            return new InsertResult(true, false, null);
        }

        public static InsertResult duplicateUsernameError() {
            return new InsertResult(false, true, "Username already exists");
        }

        public static InsertResult failure(String message) {
            return new InsertResult(false, false, message);
        }
    }
}
