package com.example.backend.service.admin;

public class InvalidAdminOrderQueryException extends RuntimeException {

    public InvalidAdminOrderQueryException(String message) {
        super(message);
    }
}
