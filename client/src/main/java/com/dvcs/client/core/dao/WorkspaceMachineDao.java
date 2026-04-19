package com.dvcs.client.core.dao;

import com.dvcs.client.core.model.WorkspaceMachine;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;

public final class WorkspaceMachineDao {

    private final MongoCollection<Document> machines;

    public WorkspaceMachineDao(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.machines = database.getCollection("workspace_machines");
    }

    public void insert(WorkspaceMachine machine) {
        Objects.requireNonNull(machine, "machine");
        machines.insertOne(machine.toDocument());
    }

    public List<WorkspaceMachine> findByWorkspaceId(ObjectId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        List<WorkspaceMachine> result = new ArrayList<>();
        for (Document doc : machines.find(Filters.eq("workspaceId", workspaceId))) {
            result.add(WorkspaceMachine.fromDocument(doc));
        }
        return result;
    }

    public Optional<WorkspaceMachine> findById(ObjectId id) {
        Document doc = machines.find(Filters.eq("_id", id)).first();
        return Optional.ofNullable(WorkspaceMachine.fromDocument(doc));
    }

    public boolean updateLastAccessed(ObjectId id) {
        return machines.updateOne(
                Filters.eq("_id", id),
                Updates.set("lastAccessedAt", new java.util.Date())
        ).getModifiedCount() > 0;
    }
}
