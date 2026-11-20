package com.example.Employee_Management.exception;

/** Thrown for a request that is well-formed JSON but can't be processed, e.g. a CSV file with a bad header. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
