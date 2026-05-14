package com.dvcs.client.workspacepage.dao;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.UpdateOptions;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;

public final class WorkspaceSettingsDAO {

    private final MongoCollection<Document> settings;

    public WorkspaceSettingsDAO(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.settings = database.getCollection("workspace_settings");
    }

    public Optional<Document> findByWorkspaceId(ObjectId workspaceId) {
        return Optional.ofNullable(settings.find(eq("workspaceId", workspaceId)).first());
    }

    /**
     * Upserts all workspace_settings schema fields for a workspace.
     */
    public void upsert(ObjectId workspaceId, String defaultBranch, String visibility,
                       String mergeStrategy, boolean requireCodeReview,
                       int maxCollaborators, String theme) {
        settings.updateOne(
                eq("workspaceId", workspaceId),
                new Document("$set", new Document("workspaceId", workspaceId)
                        .append("defaultBranch",     defaultBranch  != null ? defaultBranch  : "main")
                        .append("visibility",        visibility     != null ? visibility     : "private")
                        .append("mergeStrategy",     mergeStrategy  != null ? mergeStrategy  : "merge")
                        .append("requireCodeReview", requireCodeReview)
                        .append("maxCollaborators",  maxCollaborators > 0 ? maxCollaborators : 10)
                        .append("theme",             theme          != null ? theme          : "dark")
                        .append("updatedAt",         new Date())),
                new UpdateOptions().upsert(true));
    }
}
