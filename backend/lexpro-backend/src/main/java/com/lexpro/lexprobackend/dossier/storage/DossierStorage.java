package com.lexpro.lexprobackend.dossier.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

public interface DossierStorage {

    StoredObject put(long caseId, String extension, InputStream content, long maxBytes) throws IOException;

    StoredResource get(String objectKey) throws IOException;

    void delete(String objectKey) throws IOException;

    record StoredObject(String objectKey, long size, String sha256) {
    }

    record StoredResource(Resource resource, long size) {
    }
}
