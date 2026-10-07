package com.example.backend.service.order;

public class OrderTokenGenerationException extends RuntimeException {

    public OrderTokenGenerationException() {
        super("A unique order token could not be generated.");
    }
}
