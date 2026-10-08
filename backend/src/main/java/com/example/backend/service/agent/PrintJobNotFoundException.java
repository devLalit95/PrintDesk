package com.example.backend.service.agent;

public class PrintJobNotFoundException extends RuntimeException {

    public PrintJobNotFoundException() {
        super("The print job is unavailable.");
    }
}
