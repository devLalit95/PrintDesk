package com.example.backend.service.agent;

public class AgentNotFoundException extends RuntimeException {

    public AgentNotFoundException() {
        super("The agent is unavailable.");
    }
}
