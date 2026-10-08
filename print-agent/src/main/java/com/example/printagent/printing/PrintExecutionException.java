package com.example.printagent.printing;

public class PrintExecutionException extends RuntimeException {

    private final boolean submissionMayHaveOccurred;

    public PrintExecutionException(String message) {
        this(message, null, false);
    }

    public PrintExecutionException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public PrintExecutionException(String message, Throwable cause, boolean submissionMayHaveOccurred) {
        super(message, cause);
        this.submissionMayHaveOccurred = submissionMayHaveOccurred;
    }

    public boolean submissionMayHaveOccurred() {
        return submissionMayHaveOccurred;
    }
}
