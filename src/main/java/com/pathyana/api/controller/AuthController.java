package com.pathyana.api.controller;

import com.pathyana.api.dto.AuthResponse;
import com.pathyana.api.dto.LoginRequest;
import com.pathyana.api.dto.SignupRequest;
import com.pathyana.api.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public auth endpoints:
 *
 *   POST /api/auth/signup       — create account, send verification email
 *   GET  /api/auth/verify?token — confirm email
 *   POST /api/auth/login        — exchange credentials for JWT
 *
 * Validation: @Valid on the @RequestBody triggers field-level validation defined
 * by the annotations on the DTOs (@NotBlank, @Email, etc.). Spring returns 400
 * automatically with field-level errors if validation fails.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "message", "Account created. Check your email to verify your address.",
            "email", request.email().trim().toLowerCase()
        ));
    }

    /**
     * Clicked from the verification email. Returns a plain HTML page rather than JSON
     * because the user reaches it from their email client — they expect a friendly
     * confirmation, not a raw JSON blob.
     *
     * Later (Phase 5) we redirect to the React frontend with a query param instead.
     */
    @GetMapping(value = "/verify", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verify(@RequestParam("token") String token) {
        boolean ok = authService.verifyEmail(token);
        if (ok) {
            return ResponseEntity.ok(htmlPage(
                "Email verified",
                "Your email has been verified. You can now log in to Pathyana."
            ));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(htmlPage(
                "Verification failed",
                "This verification link is invalid or has already been used."
            ));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    private String htmlPage(String title, String body) {
        return """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"><title>%s — Pathyana</title></head>
            <body style="font-family: -apple-system, sans-serif; max-width: 480px; margin: 80px auto; padding: 24px; text-align: center;">
              <h1 style="color: #2d6a4f;">%s</h1>
              <p style="color: #444; font-size: 16px;">%s</p>
            </body></html>
            """.formatted(title, title, body);
    }
}
