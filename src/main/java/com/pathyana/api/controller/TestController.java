package com.pathyana.api.controller;

import com.pathyana.api.dto.GroqTestRequest;
import com.pathyana.api.dto.GroqTestResponse;
import com.pathyana.api.llm.GroqClient;
import com.pathyana.api.llm.GroqClient.GroqException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Phase 1 sanity-check controller. Two endpoints:
 *
 *   GET  /api/test/ping  — does the app respond? (no Groq, no DB)
 *   POST /api/test/groq  — does Groq work end-to-end?
 *
 * Both endpoints get DELETED in Phase 4 when the real meal generation endpoint
 * goes live. They're scaffolding, not part of the product.
 */
@RestController
@RequestMapping("/api/test")
public class TestController {

    private final GroqClient groqClient;

    public TestController(GroqClient groqClient) {
        this.groqClient = groqClient;
    }

    /**
     * Cheapest possible "is the server alive" check.
     * Verifies: Spring Boot started, Tomcat is listening, routing works.
     */
    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok", "service", "pathyana-backend");
    }

    /**
     * Sends a prompt to Groq and returns the response.
     * Verifies: GROQ_API_KEY is set, network reaches Groq, model responds.
     *
     * Use a plain-text prompt — JSON mode is off here so you can ask anything.
     */
    @PostMapping("/groq")
    public ResponseEntity<?> testGroq(@RequestBody GroqTestRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "prompt must not be empty"));
        }

        try {
            String response = groqClient.complete(
                "You are Pathyana, a helpful clinical nutrition assistant. Keep answers short and clear.",
                request.prompt(),
                false  // plain text, not JSON
            );
            return ResponseEntity.ok(new GroqTestResponse(
                groqClient.modelId(),
                request.prompt(),
                response
            ));
        } catch (GroqException e) {
            return ResponseEntity.status(502).body(Map.of(
                "error", "Groq call failed",
                "detail", e.getMessage()
            ));
        }
    }
}
