package com.dvcs.client.auth;

import com.dvcs.client.auth.db.MongoConnection;
import com.dvcs.client.auth.repo.UserRepository;
import com.dvcs.client.auth.service.UserService;
import com.mongodb.client.MongoDatabase;

public final class AuthExampleMain {

    public static void main(String[] args) {
        String databaseName = System.getenv("MONGODB_DB");
        if (databaseName == null || databaseName.isBlank()) {
            databaseName = "DVCS";
        }

        MongoDatabase db = MongoConnection.getDatabase(databaseName);
        UserRepository repo = new UserRepository(db);
        UserService service = new UserService(repo);

        System.out.println("--- SIGNUP ---");
        System.out.println(service.signup("alice", "P@ssw0rd!").message());

        System.out.println("--- LOGIN ---");
        System.out.println(service.login("alice", "P@ssw0rd!").message());
        System.out.println(service.login("alice", "wrong").message());

        System.out.println("--- UPDATE NAME ---");
        System.out.println(service.updateNameByUsername("alice", "Alice Doe").message());

        MongoConnection.closeClient();
    }
}
