package com.airtribe.tasktracker.attachment;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface StorageService {
    String store(UUID taskId, String originalFilename, InputStream content, long size) throws IOException;
    Resource load(String storagePath);
    void delete(String storagePath);
}
