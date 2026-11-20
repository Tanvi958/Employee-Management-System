package com.example.Employee_Management.exception;

import java.time.Instant;
import java.util.Map;

/** JSON body returned for every error response. {@code fieldErrors} is filled only for validation errors. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
}
