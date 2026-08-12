package com.lexpro.lexprobackend.dossier.storage;

import java.io.IOException;

public class StorageLimitExceededException extends IOException {

    public StorageLimitExceededException(long maxBytes) {
        super("File content exceeds the configured limit of " + maxBytes + " bytes");
    }
}
