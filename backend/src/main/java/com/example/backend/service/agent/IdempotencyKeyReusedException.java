package com.example.backend.service.agent;

public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("An event identifier cannot be reused with a different payload.");
    }
}
