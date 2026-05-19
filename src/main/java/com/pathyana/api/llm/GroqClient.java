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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thin client for Groq's Chat Completions endpoint.
 *
 * v2 — added 429 (rate limit) handling.
 *
 * Strategy when we get a 429:
 *   1. Parse the response body for a "Please try again in X" hint.
 *      Groq embeds this in the error message — we honor it when present.
 *   2. Fall back to exponential backoff if no hint is parseable: 1s, 2s, 4s.
 *   3. Cap at 3 retries total. Beyond that, propagate the failure to the caller —
 *      the orchestrator marks that meal as failed and moves on.
 *
 * Why this matters for weekly plans:
 *   - Groq's free tier is 12,000 tokens-per-minute. A weekly plan needs ~25k tokens
 *     over 3-4 minutes.
 *   - Without retries, we burn through the budget in ~10 meals and lose the rest.
 *   - With retries that respect the "try again in X" hint, the orchestrator self-paces
 *     automatically — no fixed sleep needed, just react to Groq's actual signals.
 *
 * Why we don't use Spring Retry or Resilience4j:
 *   - This is one focused retry loop. Adding a library is overkill.
 *   - Keeping the logic visible here makes it easy to tune for Groq's specific quirks.
 */
@Component
public class GroqClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final int MAX_RETRIES = 3;

    /** "Please try again in 290ms" / "1.5s" — parse seconds and ms variants. */
    private static final Pattern RETRY_HINT_MS = Pattern.compile("try again in (\\d+(?:\\.\\d+)?)ms");
    private static final Pattern RETRY_HINT_S  = Pattern.compile("try again in (\\d+(?:\\.\\d+)?)s");

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

    public String complete(String systemPrompt, String userPrompt, boolean expectJson) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GroqException("GROQ_API_KEY is not set. Export it in your shell and restart the app.");
        }

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

        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new GroqException("Failed to serialize Groq request: " + e.getMessage(), e);
        }

        int attempt = 0;
        while (true) {
            attempt++;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_URL))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

                log.debug("Calling Groq model={} expectJson={} attempt={}", model, expectJson, attempt);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                // 429 — rate limit. Retry with backoff that honors Groq's hint when possible.
                if (status == 429 && attempt <= MAX_RETRIES) {
                    long waitMs = computeRetryWaitMs(response, attempt);
                    log.warn("Groq 429 (attempt {}). Backing off {} ms.", attempt, waitMs);
                    sleep(waitMs);
                    continue;
                }

                // Other 5xx — also worth retrying once or twice
                if (status >= 500 && status < 600 && attempt <= MAX_RETRIES) {
                    long waitMs = backoffMs(attempt);
                    log.warn("Groq {} (attempt {}). Backing off {} ms.", status, attempt, waitMs);
                    sleep(waitMs);
                    continue;
                }

                if (status >= 400) {
                    log.error("Groq API error {} (attempt {}): {}", status, attempt, response.body());
                    throw new GroqException("Groq returned status " + status + ": " + truncate(response.body(), 500));
                }

                JsonNode root = objectMapper.readTree(response.body());
                String content = root.path("choices").path(0).path("message").path("content").asText();

                if (content == null || content.isBlank()) {
                    throw new GroqException("Groq returned empty content. Full response: " + truncate(response.body(), 500));
                }

                log.debug("Groq response received, {} chars", content.length());
                return content;

            } catch (GroqException e) {
                throw e;
            } catch (java.net.http.HttpTimeoutException e) {
                if (attempt <= MAX_RETRIES) {
                    long waitMs = backoffMs(attempt);
                    log.warn("Groq timeout (attempt {}). Backing off {} ms.", attempt, waitMs);
                    sleep(waitMs);
                    continue;
                }
                throw new GroqException("Groq timed out after " + MAX_RETRIES + " retries", e);
            } catch (Exception e) {
                log.error("Groq call failed (attempt {})", attempt, e);
                throw new GroqException("Groq call failed: " + e.getMessage(), e);
            }
        }
    }

    public String modelId() {
        return "groq:" + model;
    }

    // ---------- retry helpers ----------

    /**
     * Parse Groq's "Please try again in Xms" or "Xs" hint. Add a small safety
     * cushion (50 ms) so we don't slam the API exactly at the moment the window opens.
     * Falls back to exponential backoff if no hint is parseable.
     */
    private long computeRetryWaitMs(HttpResponse<String> response, int attempt) {
        // First: try standard Retry-After header (Groq doesn't always set it, but check)
        try {
            String retryAfter = response.headers().firstValue("retry-after").orElse(null);
            if (retryAfter != null) {
                // Header is in seconds per HTTP spec
                long seconds = Long.parseLong(retryAfter.trim());
                return Math.max(seconds * 1000L, 100L) + 50;
            }
        } catch (Exception ignored) {}

        // Second: parse the body for "try again in Xms" / "Xs"
        String body = response.body();
        if (body != null) {
            Matcher msMatch = RETRY_HINT_MS.matcher(body);
            if (msMatch.find()) {
                try {
                    double ms = Double.parseDouble(msMatch.group(1));
                    return Math.round(ms) + 50;
                } catch (NumberFormatException ignored) {}
            }
            Matcher sMatch = RETRY_HINT_S.matcher(body);
            if (sMatch.find()) {
                try {
                    double s = Double.parseDouble(sMatch.group(1));
                    return Math.round(s * 1000) + 50;
                } catch (NumberFormatException ignored) {}
            }
        }

        // Third: exponential backoff
        return backoffMs(attempt);
    }

    /** Exponential backoff: 1s, 2s, 4s. */
    private long backoffMs(int attempt) {
        return 1000L * (long) Math.pow(2, attempt - 1);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GroqException("Interrupted during rate-limit backoff");
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** Custom exception so controllers can map it to a clean HTTP response. */
    public static class GroqException extends RuntimeException {
        public GroqException(String msg) { super(msg); }
        public GroqException(String msg, Throwable cause) { super(msg, cause); }
    }
}
