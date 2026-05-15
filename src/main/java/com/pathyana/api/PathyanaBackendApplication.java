package com.pathyana.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main Spring Boot entry point.
 *
 * @EnableAsync turns on Spring's async machinery so that methods annotated with
 *   @Async (e.g., EmailService.sendVerificationEmail) actually run on a separate
 *   thread instead of blocking the caller.
 */
@SpringBootApplication
@EnableAsync
public class PathyanaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PathyanaBackendApplication.class, args);
    }
}
