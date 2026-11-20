package com.example.Employee_Management.exception;

/** Thrown when a request clashes with existing data, e.g. a duplicate e-mail or deleting a department in use. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
