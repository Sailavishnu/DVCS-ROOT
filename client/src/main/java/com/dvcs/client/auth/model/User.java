package com.dvcs.client.auth.model;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import org.bson.Document;
import org.bson.types.ObjectId;

public final class User {

    public static final String ROLE_USER = "user";
    public static final String ROLE_COLLABORATOR = "collaborator";

    private final ObjectId id;
    private final String username;
    private final String passwordHash;
    private final String name;
    private final String role;
    private final Instant createdAt;

    public User(ObjectId id, String username, String passwordHash, String name, String role, Instant createdAt) {
        this.id = id;
        this.username = Objects.requireNonNull(username, "username");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.name = name;
        this.role = role == null ? ROLE_USER : role;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public ObjectId getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Document toDocument() {
        Document document = new Document();
        if (id != null) {
            document.put("_id", id);
        }

        document.put("username", username);
        document.put("passwordHash", passwordHash);
        document.put("name", name);
        document.put("role", role);
        document.put("createdAt", Date.from(createdAt));
        return document;
    }

    public static User fromDocument(Document document) {
        if (document == null) {
            return null;
        }

        ObjectId id = document.getObjectId("_id");
        String username = document.getString("username");
        String passwordHash = document.getString("passwordHash");
        String name = document.getString("name");
        String role = document.getString("role");
        Date createdAt = document.getDate("createdAt");

        return new User(
                id,
                username,
                passwordHash,
                name,
                role,
                createdAt == null ? null : createdAt.toInstant());
    }
}
