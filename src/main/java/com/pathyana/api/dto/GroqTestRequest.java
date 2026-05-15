package com.pathyana.api.dto;

/**
 * Request body for POST /api/test/groq.
 *
 * Records are immutable Java data classes — perfect for DTOs.
 * Jackson auto-maps JSON fields to the record components.
 *
 * Example request:
 *   { "prompt": "What is intermittent fasting?" }
 */
public record GroqTestRequest(String prompt) {
}
