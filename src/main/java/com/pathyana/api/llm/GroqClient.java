package com.pathyana.api.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Thin client for Groq's Chat Completions endpoint.
 *
 * Groq exposes an OpenAI-compatible API. That means the request/response shape is
 * identical to OpenAI's — if we ever switch providers, only this class changes.
 *
 * Why a single complete() method with system+user prompts:
 *   - Every "agent" in Pathyana (Chef, Reviewer, Optimizer, etc.) is a one-shot call.
 *   - We don't need chat history — each agent receives a fully-formed input and
 *     returns a complete response. No multi-turn complications.
 *
 * Why response_format=json_object:
 *   - All our agents return structured JSON. This flag tells the model to guarantee
 *     valid JSON output (no markdown fences, no preamble). Saves us from string-cleaning
 *     hell.
 *
 * Phase 1: this class exists, but only the test endpoint uses it.
 * Phase 4: the orchestrator service will call it three times per meal request.
 */
@Component
public class GroqClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.model}")
    private String model;

    @Value("${groq.timeout-seconds}")
    private int timeoutSeconds;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Send a system + user prompt to Groq and get back the raw completion text.
     *
     * @param systemPrompt instructions / persona for the model
     * @param userPrompt   the actual input
     * @param expectJson   if true, asks Groq to guarantee JSON output
     * @return raw response content (string)
     * @throws GroqException on any network, auth, or API failure
     */
    public String complete(String systemPrompt, String userPrompt, boolean expectJson) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GroqException("GROQ_API_KEY is not set. Export it in your shell and restart the app.");
        }

        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("model", model);
            body.put("temperature", 0.4);
            body.put("max_tokens", 2048);
            body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ));
            if (expectJson) {
                body.put("response_format", Map.of("type", "json_object"));
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_URL))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            log.debug("Calling Groq model={} expectJson={}", model, expectJson);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.error("Groq API error {}: {}", response.statusCode(), response.body());
                throw new GroqException("Groq returned status " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();

            if (content == null || content.isBlank()) {
                throw new GroqException("Groq returned empty content. Full response: " + response.body());
            }

            log.debug("Groq response received, {} chars", content.length());
            return content;

        } catch (GroqException e) {
            throw e;
        } catch (Exception e) {
            log.error("Groq call failed", e);
            throw new GroqException("Groq call failed: " + e.getMessage(), e);
        }
    }

    public String modelId() {
        return "groq:" + model;
    }

    /** Custom exception so controllers can map it to a clean HTTP response. */
    public static class GroqException extends RuntimeException {
        public GroqException(String msg) { super(msg); }
        public GroqException(String msg, Throwable cause) { super(msg, cause); }
    }
}
