package com.dvcs.client.auth.db;

import java.util.Objects;

import org.bson.Document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

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
                // Reduce noisy Mongo driver INFO logs in terminal output.
                System.setProperty("org.slf4j.simpleLogger.log.org.mongodb.driver", "error");

                String connectionString = System.getenv("MONGODB_URI");
                if (connectionString == null || connectionString.isBlank()) {
                    connectionString = "mongodb://localhost:27017";
                }
                mongoClient = MongoClients.create(connectionString);

                // Force a lightweight check so we can print one clean status line.
                String dbName = System.getenv("MONGODB_DB");
                if (dbName == null || dbName.isBlank()) {
                    dbName = "DVCS";
                }
                mongoClient.getDatabase(dbName).runCommand(new Document("ping", 1));
                System.out.println("Connected to MongoDB");
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
