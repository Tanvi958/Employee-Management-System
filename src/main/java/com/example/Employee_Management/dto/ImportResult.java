package com.example.Employee_Management.dto;

import java.util.List;

/** Outcome of a CSV import: how many rows were added, and why the others were skipped. */
public record ImportResult(int imported, int failed, List<RowError> errors) {

    /** {@code line} is the line number in the file (the header is line 1). */
    public record RowError(int line, String message) {
    }
}
