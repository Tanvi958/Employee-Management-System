package com.example.Employee_Management.model;

/**
 * What a signed-in user may do.
 * <ul>
 *   <li>{@code ADMIN}: everything, including departments, roles, users and the audit log.</li>
 *   <li>{@code HR}: read everything, and create, edit, delete and import employees.</li>
 *   <li>{@code VIEWER}: read-only access.</li>
 * </ul>
 */
public enum UserRole {
    ADMIN,
    HR,
    VIEWER
}
