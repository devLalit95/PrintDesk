package com.example.backend.storage;

public class StorageLimitExceededException extends RuntimeException {

    public StorageLimitExceededException(long maxBytes) {
        super("Document exceeds the maximum allowed size of " + maxBytes + " bytes.");
    }
}
