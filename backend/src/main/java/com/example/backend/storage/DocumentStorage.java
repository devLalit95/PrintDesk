package com.example.backend.storage;

import java.io.IOException;
import java.io.InputStream;

public interface DocumentStorage {

    StoredDocument store(InputStream content, long maxBytes) throws IOException;

    InputStream open(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;
}
