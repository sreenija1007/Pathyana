package com.pathyana.api.dto;

/**
 * Response for POST /api/auth/login (and signup if we auto-login post-verification).
 *
 * tokenType: always "Bearer" — tells the client how to use the token in subsequent
 *            requests (Authorization: Bearer <token>).
 * expiresInSeconds: client uses this to know when to prompt for a re-login.
 */
public record AuthResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        Long userId,
        String email,
        String name
) {
}
