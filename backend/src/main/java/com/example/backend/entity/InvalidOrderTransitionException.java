package com.example.backend.entity;

public class InvalidOrderTransitionException extends RuntimeException {

    public InvalidOrderTransitionException(PrintOrderStatus current, PrintOrderStatus requested) {
        super("The print order cannot transition from " + current + " to " + requested + ".");
    }
}
