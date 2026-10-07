package com.example.backend.service.order;

public class InvalidPrintOrderRequestException extends RuntimeException {

    public InvalidPrintOrderRequestException(String message) {
        super(message);
    }
}
