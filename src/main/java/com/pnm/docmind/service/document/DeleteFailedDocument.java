package com.pnm.docmind.service.document;

import com.pnm.docmind.exception.FileStorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@Slf4j
public class DeleteFailedDocument {

    @Value("${app.storage.path}")
    private String storagePath;

    public void deleteDocument(String storedFilename) {

        log.info("Delete failed document. storedFilename={}", storedFilename);

        if (storedFilename == null || storedFilename.isBlank()) {
            throw new IllegalArgumentException("Stored filename must not be blank");
        }

        Path storageDirectory = Paths.get(storagePath).toAbsolutePath().normalize();
        Path documentPath = storageDirectory.resolve(storedFilename).normalize();

        if (!documentPath.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("Invalid stored filename");
        }

        try {
            Files.deleteIfExists(documentPath);
        } catch (IOException e) {
            throw new FileStorageException("Failed to delete stored document: " + storedFilename);
        }
    }
}
