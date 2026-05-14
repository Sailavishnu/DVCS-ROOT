package com.dvcs.client.workspacepage.dao;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import org.bson.Document;
import org.bson.types.ObjectId;

public final class UserSessionDAO {

    private final MongoCollection<Document> sessions;

    public UserSessionDAO(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.sessions = database.getCollection("user_sessions");
    }

    /** Creates a new session on login. Returns the inserted session _id. */
    public ObjectId createSession(ObjectId userId, String ipAddress, String userAgent) {
        ObjectId id = new ObjectId();
        Date now = new Date();
        Date expires = new Date(now.getTime() + 24L * 60 * 60 * 1000);
        sessions.insertOne(new Document("_id", id)
                .append("userId", userId)
                .append("sessionToken", UUID.randomUUID().toString())
                .append("ipAddress",  ipAddress  != null ? ipAddress  : "unknown")
                .append("userAgent",  userAgent   != null ? userAgent   : "DVCS Desktop Client")
                .append("loginAt",    now)
                .append("expiresAt",  expires)
                .append("lastPingAt", now)
                .append("isActive",   true));
        return id;
    }

    /** Marks a session inactive (logout / app close). */
    public void deactivateSession(ObjectId sessionId) {
        sessions.updateOne(eq("_id", sessionId),
                new Document("$set", new Document("isActive", false)));
    }

    /** Updates lastPingAt — call periodically to keep session alive. */
    public void ping(ObjectId sessionId) {
        sessions.updateOne(eq("_id", sessionId),
                new Document("$set", new Document("lastPingAt", new Date())));
    }

    /** Force-deactivate all active sessions for a user. */
    public void deactivateAllForUser(ObjectId userId) {
        sessions.updateMany(and(eq("userId", userId), eq("isActive", true)),
                new Document("$set", new Document("isActive", false)));
    }
}
