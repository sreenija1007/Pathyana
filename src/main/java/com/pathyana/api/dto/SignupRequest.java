package com.pathyana.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for POST /api/auth/signup.
 *
 * Validation annotations are enforced by @Valid in the controller — Spring
 * returns a 400 with field-level errors if any constraint fails.
 *
 * password: 8+ chars minimum. We deliberately don't enforce complex rules
 * (special chars, mixed case) — current NIST guidance is that length > complexity,
 * and forcing weird rules tends to make users pick predictable patterns.
 */
public record SignupRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be 2-100 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be 8-100 characters")
        String password
) {
}
