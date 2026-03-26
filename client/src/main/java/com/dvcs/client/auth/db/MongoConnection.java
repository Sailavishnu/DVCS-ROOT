package com.dvcs.client.auth.db;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.util.Objects;

public final class MongoConnection {

    private static volatile MongoClient mongoClient;

    private MongoConnection() {
    }

    public static MongoClient getClient() {
        MongoClient existing = mongoClient;
        if (existing != null) {
            return existing;
        }

        synchronized (MongoConnection.class) {
            if (mongoClient == null) {
                String connectionString = System.getenv("MONGODB_URI");
                if (connectionString == null || connectionString.isBlank()) {
                    connectionString = "mongodb://localhost:27017";
                }
                mongoClient = MongoClients.create(connectionString);
            }
            return mongoClient;
        }
    }

    public static MongoDatabase getDatabase(String databaseName) {
        Objects.requireNonNull(databaseName, "databaseName");
        return getClient().getDatabase(databaseName);
    }

    public static void closeClient() {
        MongoClient existing = mongoClient;
        if (existing != null) {
            existing.close();
            mongoClient = null;
        }
    }
}
