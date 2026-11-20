package com.example.Employee_Management.config;

import com.example.Employee_Management.model.AppUser;
import com.example.Employee_Management.model.UserRole;
import com.example.Employee_Management.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes sure someone can sign in. When there are no users yet it creates an admin from
 * {@code ADMIN_USERNAME} / {@code ADMIN_PASSWORD}; with sample data on ({@code app.seed-data=true}) it also
 * adds demo HR and viewer accounts.
 */
@Component
@Order(0)
public class UserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final boolean demoUsers;

    public UserSeeder(AppUserRepository users, PasswordEncoder passwordEncoder,
                      @Value("${app.admin.username}") String adminUsername,
                      @Value("${app.admin.password}") String adminPassword,
                      @Value("${app.seed-data:true}") boolean demoUsers) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.demoUsers = demoUsers;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        users.save(new AppUser(adminUsername.toLowerCase(), "Administrator",
                passwordEncoder.encode(adminPassword), UserRole.ADMIN));
        if (demoUsers) {
            users.save(new AppUser("hr", "Ananya Singh", passwordEncoder.encode("hr123456"), UserRole.HR));
            users.save(new AppUser("viewer", "Guest Viewer", passwordEncoder.encode("viewer123"), UserRole.VIEWER));
        }
        log.info("Created admin user '{}'{}", adminUsername, demoUsers ? " and demo users 'hr' and 'viewer'" : "");
    }
}
