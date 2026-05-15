package com.pathyana.api.dto;

/**
 * Response body for POST /api/test/groq.
 *
 * model    — identifier of the LLM that responded (useful for telemetry)
 * prompt   — echo of what we sent, so it's easy to verify in tests
 * response — Groq's actual completion text
 */
public record GroqTestResponse(String model, String prompt, String response) {
}
