package com.airtribe.tasktracker.attachment;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LocalDiskStorageService implements StorageService {

    private final Path rootDir;

    public LocalDiskStorageService(StorageProperties properties) {
        this.rootDir = Path.of(properties.getRootDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create storage root directory", e);
        }
    }

    @Override
    public String store(UUID taskId, String originalFilename, InputStream content, long size) throws IOException {
        Path taskDir = requireWithinRoot(rootDir.resolve(taskId.toString()).normalize(), rootDir);
        Files.createDirectories(taskDir);
        String storedName = UUID.randomUUID() + "_" + safeFilename(originalFilename);
        Path target = requireWithinRoot(taskDir.resolve(storedName).normalize(), taskDir);
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return rootDir.relativize(target).toString();
    }

    @Override
    public Resource load(String storagePath) {
        Path target = requireWithinRoot(rootDir.resolve(storagePath).normalize(), rootDir);
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String storagePath) {
        Path target = requireWithinRoot(rootDir.resolve(storagePath).normalize(), rootDir);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to delete attachment file", e);
        }
    }

    private Path requireWithinRoot(Path candidate, Path boundary) {
        if (!candidate.startsWith(boundary)) {
            throw new IllegalArgumentException("Invalid storage path.");
        }
        return candidate;
    }

    private String safeFilename(String original) {
        String name = original == null ? "file" : Path.of(original).getFileName().toString();
        return name.isBlank() ? "file" : name;
    }
}
