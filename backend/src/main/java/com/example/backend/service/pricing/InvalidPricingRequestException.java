package com.example.backend.service.pricing;

public class InvalidPricingRequestException extends RuntimeException {

    public InvalidPricingRequestException(String message) {
        super(message);
    }
}
