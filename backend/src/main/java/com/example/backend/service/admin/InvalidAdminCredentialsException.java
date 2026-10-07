package com.example.backend.service.admin;

public class InvalidAdminCredentialsException extends RuntimeException {

    public InvalidAdminCredentialsException() {
        super("The username or password is invalid.");
    }
}
