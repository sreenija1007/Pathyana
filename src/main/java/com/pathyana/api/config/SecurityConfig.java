package com.pathyana.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration.
 *
 * PHASE 1: completely open — no auth on any endpoint. We need to verify Groq works
 * end-to-end before we layer JWT on top. Adding auth now would just give us two
 * failure modes to debug instead of one.
 *
 * PHASE 4 (later): we replace this with JWT-based auth. Public endpoints (signup,
 * login, healthcheck) will be permitAll; everything else requires a valid token.
 *
 * Do not deploy this configuration anywhere users can reach. Local dev only.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())               // we use stateless REST, no CSRF tokens
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
