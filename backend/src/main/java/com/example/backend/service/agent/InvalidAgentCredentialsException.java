package com.example.backend.service.agent;

public class InvalidAgentCredentialsException extends RuntimeException {

    public InvalidAgentCredentialsException() {
        super("The agent credentials are invalid.");
    }
}
