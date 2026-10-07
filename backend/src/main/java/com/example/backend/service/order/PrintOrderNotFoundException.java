package com.example.backend.service.order;

public class PrintOrderNotFoundException extends RuntimeException {

    public PrintOrderNotFoundException() {
        super("The requested print order was not found.");
    }
}
