package com.example.Employee_Management.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Who is making the current request, for audit entries. */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** The signed-in username, or {@code "system"} for start-up jobs and anonymous calls. */
    public static String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return "system";
        }
        return auth.getName();
    }
}
