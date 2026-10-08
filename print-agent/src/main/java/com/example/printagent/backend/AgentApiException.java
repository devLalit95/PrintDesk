package com.example.printagent.backend;

public final class AgentApiException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public AgentApiException(int statusCode, String errorCode) {
        super("The PrintDesk backend request failed with HTTP " + statusCode
                + (errorCode == null ? "." : " (" + errorCode + ")."));
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
