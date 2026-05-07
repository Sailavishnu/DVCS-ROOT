package com.dvcs.client.auth.service;

import com.dvcs.client.auth.model.User;
import com.dvcs.client.auth.repo.UserRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.mindrot.jbcrypt.BCrypt;

public final class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    public AuthResult signup(String username, String password) {
        return signup(username, password, null);
    }

    public AuthResult signup(String username, String password, String role) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername.isEmpty()) {
            return AuthResult.error("Username is required");
        }
        if (password == null || password.isBlank()) {
            return AuthResult.error("Password is required");
        }

        String normalizedRole = role == null ? User.ROLE_USER : role.trim();
        if (normalizedRole.isEmpty()) {
            normalizedRole = User.ROLE_USER;
        }
        if (!User.ROLE_USER.equals(normalizedRole) && !User.ROLE_COLLABORATOR.equals(normalizedRole)) {
            return AuthResult.error("Invalid role");
        }

        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            return AuthResult.error("Username already exists");
        }

        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));
        User user = new User(null, normalizedUsername, passwordHash, null, normalizedRole, Instant.now());

        UserRepository.InsertResult inserted = userRepository.insert(user);
        if (inserted.success()) {
            return AuthResult.success("Signup successful");
        }
        if (inserted.duplicateUsername()) {
            return AuthResult.error("Username already exists");
        }
        return AuthResult.error(inserted.errorMessage() == null ? "Signup failed" : inserted.errorMessage());
    }

    public AuthResult login(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername.isEmpty()) {
            return AuthResult.error("Username is required");
        }
        if (password == null) {
            return AuthResult.error("Password is required");
        }

        Optional<User> userOpt = userRepository.findByUsername(normalizedUsername);
        if (userOpt.isEmpty()) {
            return AuthResult.error("Invalid username or password");
        }

        User user = userOpt.get();
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            return AuthResult.error("Invalid username or password");
        }

        boolean ok = BCrypt.checkpw(password, user.getPasswordHash());
        if (!ok) {
            return AuthResult.error("Invalid username or password");
        }
        return AuthResult.success("Login successful");
    }

    public AuthResult updateNameByUsername(String username, String newName) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername.isEmpty()) {
            return AuthResult.error("Username is required");
        }

        // newName is allowed to be null (nullable per requirements)
        boolean updated = userRepository.updateNameByUsername(normalizedUsername, newName);
        if (!updated) {
            return AuthResult.error("User not found or name unchanged");
        }
        return AuthResult.success("Name updated");
    }

    public AuthResult updateNameByUserId(String userIdHex, String newName) {
        if (userIdHex == null || userIdHex.isBlank()) {
            return AuthResult.error("UserId is required");
        }

        ObjectId objectId;
        try {
            objectId = new ObjectId(userIdHex);
        } catch (IllegalArgumentException e) {
            return AuthResult.error("Invalid userId");
        }

        boolean updated = userRepository.updateNameById(objectId, newName);
        if (!updated) {
            return AuthResult.error("User not found or name unchanged");
        }
        return AuthResult.success("Name updated");
    }

    private static String normalizeUsername(String username) {
        if (username == null) {
            return "";
        }
        return username.trim();
    }

    public record AuthResult(boolean success, String message) {
        public static AuthResult success(String message) {
            return new AuthResult(true, message);
        }

        public static AuthResult error(String message) {
            return new AuthResult(false, message);
        }
    }
}
