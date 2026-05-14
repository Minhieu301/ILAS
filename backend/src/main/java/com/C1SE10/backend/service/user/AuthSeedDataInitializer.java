package com.C1SE10.backend.service.user;

import com.C1SE10.backend.model.UserAccount;
import com.C1SE10.backend.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthSeedDataInitializer implements CommandLineRunner {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedPassword("user@gmail.com", "user", "User@123");
        seedPassword("moderator@gmail.com", "moderator", "Moderator@123");
        seedPassword("admin@gmail.com", "admin", "Admin@123");
    }

    private void seedPassword(String email, String username, String rawPassword) {
        userAccountRepository.findByUsernameOrEmail(username, email).ifPresent(user -> {
            String encodedPassword = passwordEncoder.encode(rawPassword);
            user.setPasswordHash(encodedPassword);
            user.setIsActive(true);
            userAccountRepository.save(user);
        });
    }
}