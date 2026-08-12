package com.lexpro.lexprobackend.dossier.storage;

import com.lexpro.lexprobackend.dossier.config.DossierStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalDossierStorageTests {

    @TempDir
    Path temporaryRoot;

    @Test
    void shouldStoreHashReadAndDeleteWithinConfiguredRoot() throws Exception {
        DossierStorageProperties properties = new DossierStorageProperties();
        properties.setLocalRoot(temporaryRoot);
        LocalDossierStorage storage = new LocalDossierStorage(properties);

        DossierStorage.StoredObject stored = storage.put(9L, "txt",
                new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)), 100L);

        assertEquals(3L, stored.size());
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", stored.sha256());
        assertEquals("abc", new String(storage.get(stored.objectKey()).resource()
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8));

        storage.delete(stored.objectKey());
        assertThrows(IOException.class, () -> storage.get(stored.objectKey()));
    }

    @Test
    void shouldRejectEscapingObjectKeyAndStreamingLimitOverflow() {
        DossierStorageProperties properties = new DossierStorageProperties();
        properties.setLocalRoot(temporaryRoot);
        LocalDossierStorage storage = new LocalDossierStorage(properties);

        assertThrows(IllegalArgumentException.class, () -> storage.get("../outside.txt"));
        assertThrows(StorageLimitExceededException.class, () -> storage.put(9L, "txt",
                new ByteArrayInputStream("too-large".getBytes(StandardCharsets.UTF_8)), 3L));
    }
}
