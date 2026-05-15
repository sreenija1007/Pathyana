package com.pathyana.api.service;

import com.pathyana.api.dto.AuthResponse;
import com.pathyana.api.dto.LoginRequest;
import com.pathyana.api.dto.SignupRequest;
import com.pathyana.api.model.User;
import com.pathyana.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

/**
 * Auth business logic — signup, verify, login.
 *
 * Each public method maps to one controller endpoint. Controllers stay thin:
 * receive DTO, call service, return response.
 *
 * Why @Transactional on signup: we want the User insert + verification token storage
 * to either both succeed or both fail. Without a transaction, a crash mid-method could
 * leave a half-created user in the DB.
 *
 * Login security notes:
 *  - Email check and password check return the same generic error message ("Invalid
 *    credentials"). This prevents email enumeration — an attacker can't tell whether
 *    they have the right email but wrong password, vs no account at all.
 *  - We do NOT reveal "email not verified" until AFTER password is verified, for the
 *    same reason. (Some apps don't even reveal this — they just refuse login silently.
 *    We err on the side of helpful UX once auth is confirmed.)
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
    }

    @Transactional
    public void signup(SignupRequest request) {
        // Normalize email (case-insensitive uniqueness) before any check or save.
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "An account with this email already exists");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEmailVerified(false);
        user.setProfileCompleted(false);
        user.setVerificationToken(UUID.randomUUID().toString());

        userRepository.save(user);
        log.info("New user signed up: {}", email);

        // Async — won't block the response.
        emailService.sendVerificationEmail(email, user.getName(), user.getVerificationToken());
    }

    @Transactional
    public boolean verifyEmail(String token) {
        Optional<User> opt = userRepository.findByVerificationToken(token);
        if (opt.isEmpty()) {
            return false;
        }
        User user = opt.get();
        user.setEmailVerified(true);
        user.setVerificationToken(null);  // single-use
        userRepository.save(user);
        log.info("Email verified for user: {}", user.getEmail());
        return true;
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Please verify your email before logging in");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        log.info("User logged in: {}", email);

        return new AuthResponse(
            token,
            "Bearer",
            jwtService.getExpirationSeconds(),
            user.getId(),
            user.getEmail(),
            user.getName()
        );
    }
}
