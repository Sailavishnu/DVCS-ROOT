package com.dvcs.client.auth.model;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import org.bson.Document;
import org.bson.types.ObjectId;

public final class User {

    public static final String ROLE_USER = "user";
    public static final String ROLE_COLLABORATOR = "collaborator";

    public static class Name {
        private final String firstName;
        private final String lastName;

        public Name(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }

        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        
        public String getFullName() {
            return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
        }
    }

    private final ObjectId id;
    private final String username;
    private final String passwordHash;
    private final Name name;
    private final String role;
    
    // Derived schema fields
    private final Long storageQuota;
    private final Integer filesLimit;
    private final Instant lastActiveAt;
    private final String collabStatus;
    private final Integer totalFilesShared;
    private final Instant createdAt;

    public User(ObjectId id, String username, String passwordHash, Name name, String role, 
                Long storageQuota, Integer filesLimit, Instant lastActiveAt, 
                String collabStatus, Integer totalFilesShared, Instant createdAt) {
        this.id = id;
        this.username = Objects.requireNonNull(username, "username");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.name = name;
        this.role = role == null ? ROLE_USER : role;
        
        this.storageQuota = storageQuota;
        this.filesLimit = filesLimit;
        this.lastActiveAt = lastActiveAt;
        this.collabStatus = collabStatus;
        this.totalFilesShared = totalFilesShared;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    // Convenience constructor for existing code
    public User(ObjectId id, String username, String passwordHash, Name name, String role, Instant createdAt) {
        this(id, username, passwordHash, name, role, null, null, null, null, null, createdAt);
    }

    public ObjectId getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Name getName() { return name; }
    public String getRole() { return role; }
    public Long getStorageQuota() { return storageQuota; }
    public Integer getFilesLimit() { return filesLimit; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public String getCollabStatus() { return collabStatus; }
    public Integer getTotalFilesShared() { return totalFilesShared; }
    public Instant getCreatedAt() { return createdAt; }

    public Document toDocument() {
        Document document = new Document();
        if (id != null) {
            document.put("_id", id);
        }

        document.put("username", username);
        document.put("passwordHash", passwordHash);
        
        if (name != null) {
            document.put("name", new Document("firstName", name.firstName).append("lastName", name.lastName));
        } else {
            document.put("name", null);
        }
        
        document.put("role", role);
        document.put("storageQuota", storageQuota);
        document.put("filesLimit", filesLimit);
        document.put("lastActiveAt", lastActiveAt != null ? Date.from(lastActiveAt) : null);
        document.put("collabStatus", collabStatus);
        document.put("totalFilesShared", totalFilesShared);
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
        
        Name name = null;
        Document nameDoc = document.get("name", Document.class);
        if (nameDoc != null) {
            name = new Name(nameDoc.getString("firstName"), nameDoc.getString("lastName"));
        }
        
        String role = document.getString("role");
        
        // Handle numerics carefully from Mongo
        Number quotaNum = document.get("storageQuota", Number.class);
        Long storageQuota = quotaNum != null ? quotaNum.longValue() : null;
        
        Number limitNum = document.get("filesLimit", Number.class);
        Integer filesLimit = limitNum != null ? limitNum.intValue() : null;
        
        Date lastActiveDate = document.getDate("lastActiveAt");
        Instant lastActiveAt = lastActiveDate != null ? lastActiveDate.toInstant() : null;
        
        String collabStatus = document.getString("collabStatus");
        
        Number sharedNum = document.get("totalFilesShared", Number.class);
        Integer totalFilesShared = sharedNum != null ? sharedNum.intValue() : null;

        Date createdAt = document.getDate("createdAt");

        return new User(
                id,
                username,
                passwordHash,
                name,
                role,
                storageQuota,
                filesLimit,
                lastActiveAt,
                collabStatus,
                totalFilesShared,
                createdAt == null ? null : createdAt.toInstant());
    }
}
