package com.example.backend.service.document;

import java.nio.file.Path;

@FunctionalInterface
public interface DocxPageCounter {

    int countPages(Path docxFile);
}
