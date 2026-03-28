package com.dvcs.client.workspacepage.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Objects;

public final class FileSystemService {

    public Path normalizeWorkspaceRoot(String rawWorkspacePath) {
        if (rawWorkspacePath == null || rawWorkspacePath.isBlank()) {
            throw new IllegalArgumentException("Workspace path is missing");
        }
        return Path.of(rawWorkspacePath).toAbsolutePath().normalize();
    }

    public Path createFolder(Path workspaceRoot, String folderName) throws IOException {
        Path root = normalizeRoot(workspaceRoot);
        String normalizedFolderName = normalizeSegment(folderName, "Folder name is required");
        Path target = resolveInsideWorkspace(root, normalizedFolderName);
        return Files.createDirectories(target);
    }

    public Path createFile(Path workspaceRoot, String fileName) throws IOException {
        Path root = normalizeRoot(workspaceRoot);
        String normalizedFileName = normalizeSegment(fileName, "File name is required");
        Path target = resolveInsideWorkspace(root, normalizedFileName);

        if (Files.notExists(target)) {
            Files.createFile(target);
        }
        return target;
    }

    public Path importFile(Path workspaceRoot, Path sourceFile) throws IOException {
        Path root = normalizeRoot(workspaceRoot);
        Objects.requireNonNull(sourceFile, "sourceFile");
        if (!Files.exists(sourceFile) || !Files.isRegularFile(sourceFile)) {
            throw new IllegalArgumentException("Selected file does not exist");
        }

        String targetName = normalizeSegment(sourceFile.getFileName().toString(), "Invalid source file name");
        Path destination = resolveInsideWorkspace(root, targetName);
        return Files.copy(sourceFile, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    public String readFile(Path filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath");
        if (Files.notExists(filePath)) {
            return "";
        }
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    public void writeFile(Path filePath, String content) throws IOException {
        Objects.requireNonNull(filePath, "filePath");
        Path parent = filePath.getParent();
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent);
        }
        Files.writeString(filePath, content == null ? "" : content, StandardCharsets.UTF_8);
    }

    public Instant getLastModified(Path filePath) {
        try {
            FileTime time = Files.getLastModifiedTime(filePath);
            return time.toInstant();
        } catch (IOException e) {
            return null;
        }
    }

    private static Path normalizeRoot(Path workspaceRoot) {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        return workspaceRoot.toAbsolutePath().normalize();
    }

    private static String normalizeSegment(String value, String errorMessage) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
        if (normalized.contains("..") || normalized.contains("/") || normalized.contains("\\")) {
            throw new IllegalArgumentException("Nested paths are not supported here");
        }
        return normalized;
    }

    private static Path resolveInsideWorkspace(Path workspaceRoot, String name) {
        Path target = workspaceRoot.resolve(name).normalize();
        if (!target.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Path is outside workspace");
        }
        return target;
    }
}
