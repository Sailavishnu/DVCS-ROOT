package com.dvcs.client.core.dao;

import com.dvcs.client.core.model.Branch;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;

public final class BranchDao {

    private final MongoCollection<Document> branches;

    public BranchDao(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.branches = database.getCollection("branches");
    }

    public void insert(Branch branch) {
        Objects.requireNonNull(branch, "branch");
        branches.insertOne(branch.toDocument());
    }

    public List<Branch> findByWorkspaceId(ObjectId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        List<Branch> result = new ArrayList<>();
        for (Document doc : branches.find(Filters.eq("workspaceId", workspaceId))) {
            result.add(Branch.fromDocument(doc));
        }
        return result;
    }

    public Optional<Branch> findById(ObjectId id) {
        Document doc = branches.find(Filters.eq("_id", id)).first();
        return Optional.ofNullable(Branch.fromDocument(doc));
    }

    public Optional<Branch> findDefaultBranch(ObjectId workspaceId) {
        Document doc = branches.find(Filters.and(
                Filters.eq("workspaceId", workspaceId),
                Filters.eq("isDefault", true)
        )).first();
        return Optional.ofNullable(Branch.fromDocument(doc));
    }

    public boolean updateHeadCommit(ObjectId branchId, ObjectId commitId) {
        return branches.updateOne(
                Filters.eq("_id", branchId),
                new Document("$set", new Document("headCommitId", commitId).append("updatedAt", new java.util.Date()))
        ).getModifiedCount() > 0;
    }
}
