package com.dvcs.client.dashboard.data.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.Indexes;

public final class FileDao {

    private final MongoCollection<Document> files;

    public FileDao(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.files = database.getCollection("files");
        ensureIndexes();
    }

    private void ensureIndexes() {
        files.createIndex(Indexes.ascending("folderId"));
        files.createIndex(Indexes.ascending("filename"));
    }

    public ObjectId insert(Document fileDocument) {
        Objects.requireNonNull(fileDocument, "fileDocument");
        files.insertOne(fileDocument);
        return fileDocument.getObjectId("_id");
    }

    public List<Document> findByFolderIds(List<ObjectId> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return List.of();
        }
        return files.find(new Document("folderId", new Document("$in", folderIds))).into(new ArrayList<>());
    }

    public long countByFolderIds(List<ObjectId> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return 0;
        }
        return files.countDocuments(new Document("folderId", new Document("$in", folderIds)));
    }

    public List<Document> findByIds(List<ObjectId> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        return files.find(new Document("_id", new Document("$in", fileIds))).into(new ArrayList<>());
    }

    public List<Document> searchByFileName(String query) {
        Objects.requireNonNull(query, "query");
        Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
        return files.find(new Document("filename", pattern)).into(new ArrayList<>());
    }

    public Optional<Document> findById(ObjectId fileId) {
        Objects.requireNonNull(fileId, "fileId");
        return Optional.ofNullable(files.find(eq("_id", fileId)).first());
    }
}
