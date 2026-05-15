package com.pathyana.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing setup.
 *
 * BCrypt is the right default for password hashing in 2026 — it's adaptive (slow on purpose),
 * the cost factor scales with hardware, and salts are generated and embedded automatically.
 *
 * Cost factor 12 means 2^12 = 4096 rounds. Each hash takes ~250ms on modern hardware.
 * That's fast enough for legitimate logins, slow enough to make brute force impractical.
 *
 * Why this is in its own file rather than SecurityConfig:
 *   - SecurityConfig needs to inject PasswordEncoder. If both beans are in the same @Configuration
 *     class, Spring sometimes complains about circular references.
 *   - Smaller files are easier to understand.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
