package com.pathyana.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves brand/free-text drug names to canonical generic names via NIH RxNorm.
 *
 * Strategy:
 *   1. Try findRxcuiByString (exact + normalized search) — fast, high precision.
 *   2. If that fails, fall back to getApproximateMatch — fuzzy, lower precision.
 *   3. Pull the ingredient name from the matched concept.
 *   4. Cache results in memory.
 *
 * Failure modes:
 *   - Network timeout → Optional.empty(); caller falls back to whatever the user typed.
 *   - The API returning malformed JSON → same fallback.
 *   - This service is best-effort. We never block a user from saving a med.
 */
@Service
public class RxNormService {

    private static final Logger log = LoggerFactory.getLogger(RxNormService.class);
    private static final String BASE = "https://rxnav.nlm.nih.gov/REST";

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final ConcurrentHashMap<String, Optional<String>> cache = new ConcurrentHashMap<>();

    public Optional<String> resolveToGeneric(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        String key = input.trim().toLowerCase();
        if (cache.containsKey(key)) return cache.get(key);

        Optional<String> result = doResolve(input.trim());
        cache.put(key, result);
        return result;
    }

    private Optional<String> doResolve(String input) {
        try {
            String rxcui = findRxcuiByString(input);
            if (rxcui == null) rxcui = getApproximateMatchRxcui(input);
            if (rxcui == null) {
                log.debug("RxNorm: no match for '{}'", input);
                return Optional.empty();
            }

            String ingredient = getIngredientName(rxcui);
            if (ingredient != null) {
                log.debug("RxNorm: '{}' -> rxcui {} -> '{}'", input, rxcui, ingredient);
                return Optional.of(ingredient.toLowerCase());
            }
            return Optional.empty();

        } catch (Exception e) {
            log.warn("RxNorm lookup failed for '{}': {}", input, e.getMessage());
            return Optional.empty();
        }
    }

    private String findRxcuiByString(String name) throws Exception {
        String url = BASE + "/rxcui.json?name=" + enc(name) + "&search=2";
        JsonNode root = get(url);
        JsonNode ids = root.path("idGroup").path("rxnormId");
        if (ids.isArray() && ids.size() > 0) {
            return ids.get(0).asText();
        }
        return null;
    }

    private String getApproximateMatchRxcui(String name) throws Exception {
        String url = BASE + "/approximateTerm.json?term=" + enc(name) + "&maxEntries=1";
        JsonNode root = get(url);
        JsonNode candidates = root.path("approximateGroup").path("candidate");
        if (candidates.isArray() && candidates.size() > 0) {
            return candidates.get(0).path("rxcui").asText();
        }
        return null;
    }

    private String getIngredientName(String rxcui) throws Exception {
        String url = BASE + "/rxcui/" + rxcui + "/related.json?tty=IN";
        JsonNode root = get(url);
        JsonNode groups = root.path("relatedGroup").path("conceptGroup");
        if (groups.isArray()) {
            for (JsonNode group : groups) {
                if ("IN".equals(group.path("tty").asText())) {
                    JsonNode props = group.path("conceptProperties");
                    if (props.isArray() && props.size() > 0) {
                        return props.get(0).path("name").asText();
                    }
                }
            }
        }
        String propsUrl = BASE + "/rxcui/" + rxcui + "/properties.json";
        JsonNode propsRoot = get(propsUrl);
        String name = propsRoot.path("properties").path("name").asText(null);
        return (name == null || name.isBlank()) ? null : name;
    }

    private JsonNode get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(4))
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("RxNorm HTTP " + response.statusCode());
        }
        return mapper.readTree(response.body());
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
