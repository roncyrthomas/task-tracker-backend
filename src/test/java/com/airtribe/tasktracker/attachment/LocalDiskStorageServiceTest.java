package com.airtribe.tasktracker.attachment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDiskStorageServiceTest {

    private LocalDiskStorageService service(@TempDir Path tempDir) {
        StorageProperties properties = new StorageProperties();
        properties.setRootDir(tempDir.toString());
        return new LocalDiskStorageService(properties);
    }

    @Test
    void storesAndLoadsFileContent(@TempDir Path tempDir) throws Exception {
        LocalDiskStorageService storage = service(tempDir);
        UUID taskId = UUID.randomUUID();
        byte[] content = "hello attachment".getBytes(StandardCharsets.UTF_8);

        String storagePath = storage.store(taskId, "notes.txt", new ByteArrayInputStream(content), content.length);
        Resource resource = storage.load(storagePath);

        try (InputStream in = resource.getInputStream()) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello attachment");
        }
    }

    @Test
    void deleteRemovesTheStoredFile(@TempDir Path tempDir) throws Exception {
        LocalDiskStorageService storage = service(tempDir);
        UUID taskId = UUID.randomUUID();
        byte[] content = "bye".getBytes(StandardCharsets.UTF_8);
        String storagePath = storage.store(taskId, "notes.txt", new ByteArrayInputStream(content), content.length);

        storage.delete(storagePath);

        assertThat(storage.load(storagePath).exists()).isFalse();
    }

    @Test
    void sanitizesPathTraversalAttemptsInFilename(@TempDir Path tempDir) throws Exception {
        LocalDiskStorageService storage = service(tempDir);
        UUID taskId = UUID.randomUUID();
        byte[] content = "evil".getBytes(StandardCharsets.UTF_8);

        String storagePath = storage.store(taskId, "../../evil.txt", new ByteArrayInputStream(content), content.length);

        assertThat(storagePath).doesNotContain("..");
        assertThat(tempDir.resolve(storagePath).normalize().startsWith(tempDir)).isTrue();
    }
}
