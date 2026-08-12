package com.lexpro.lexprobackend.dossier.storage;

import com.lexpro.lexprobackend.dossier.config.DossierStorageProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class LocalDossierStorage implements DossierStorage {

    private static final Pattern SAFE_EXTENSION = Pattern.compile("[a-z0-9]{1,10}");
    private final Path root;

    public LocalDossierStorage(DossierStorageProperties properties) {
        if (properties.getLocalRoot() == null) {
            throw new IllegalStateException("lexpro.dossier.local-root must be configured");
        }
        this.root = properties.getLocalRoot().toAbsolutePath().normalize();
    }

    @Override
    public StoredObject put(long caseId, String extension, InputStream content, long maxBytes) throws IOException {
        if (caseId <= 0 || maxBytes <= 0) {
            throw new IllegalArgumentException("caseId and maxBytes must be positive");
        }
        String normalizedExtension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (!SAFE_EXTENSION.matcher(normalizedExtension).matches()) {
            throw new IllegalArgumentException("Unsafe file extension");
        }

        String objectKey = "cases/" + caseId + "/" + UUID.randomUUID() + "." + normalizedExtension;
        Path target = resolveObjectKey(objectKey);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        MessageDigest digest = sha256Digest();
        long size = 0;
        boolean moved = false;
        try (OutputStream output = Files.newOutputStream(temporary)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = content.read(buffer)) != -1) {
                size += read;
                if (size > maxBytes) {
                    throw new StorageLimitExceededException(maxBytes);
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
            if (size == 0) {
                throw new IOException("Empty files are not stored");
            }
            moveIntoPlace(temporary, target);
            moved = true;
            return new StoredObject(objectKey, size, HexFormat.of().formatHex(digest.digest()));
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    @Override
    public StoredResource get(String objectKey) throws IOException {
        Path path = resolveObjectKey(objectKey);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Stored object does not exist");
        }
        return new StoredResource(new FileSystemResource(path), Files.size(path));
    }

    @Override
    public void delete(String objectKey) throws IOException {
        Files.deleteIfExists(resolveObjectKey(objectKey));
    }

    private Path resolveObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Invalid object key");
        }
        Path relative = Path.of(objectKey);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Object key must be relative");
        }
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Object key escapes the storage root");
        }
        return resolved;
    }

    private void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
