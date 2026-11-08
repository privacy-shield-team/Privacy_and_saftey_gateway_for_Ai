package com.aiprivacy.shield.service;

import com.aiprivacy.shield.entity.User;
import com.aiprivacy.shield.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * User management service.
 * All passwords are stored as BCrypt hashes. Plain text is never persisted or logged.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Register a new local user.
     * Returns null if email already exists.
     */
    public User register(String name, String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            return null; // duplicate email
        }
        String hash = passwordEncoder.encode(rawPassword);
        User user = new User(email, hash, name);
        user.setProvider("LOCAL");
        return userRepository.save(user);
    }

    /**
     * Authenticate a user by email and raw password.
     * Updates lastLoginAt on success.
     */
    public Optional<User> authenticate(String email, String rawPassword) {
        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) return Optional.empty();

        User user = maybeUser.get();
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            return Optional.empty();
        }
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        return Optional.of(user);
    }

    /**
     * Find or create a Google OAuth user.
     */
    public User findOrCreateGoogleUser(String googleId, String email, String name) {
        Optional<User> byGoogleId = userRepository.findByGoogleId(googleId);
        if (byGoogleId.isPresent()) {
            User u = byGoogleId.get();
            u.setLastLoginAt(LocalDateTime.now());
            return userRepository.save(u);
        }
        // Check if email already exists (user registered manually then links Google)
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User u = byEmail.get();
            u.setGoogleId(googleId);
            u.setProvider("GOOGLE");
            u.setLastLoginAt(LocalDateTime.now());
            return userRepository.save(u);
        }
        // New Google user
        User newUser = new User(email, "", name);
        newUser.setGoogleId(googleId);
        newUser.setProvider("GOOGLE");
        newUser.setLastLoginAt(LocalDateTime.now());
        return userRepository.save(newUser);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }
}
