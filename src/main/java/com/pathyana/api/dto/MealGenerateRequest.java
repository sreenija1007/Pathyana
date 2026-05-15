package com.pathyana.api.dto;

/**
 * Body for POST /api/meals/generate.
 *
 * userRequest:   free-form text from the user — "give me a quick dinner", "vegetarian breakfast", etc.
 * profileOverride: OPTIONAL. If present, overrides the authenticated user's stored profile
 *                  fields. Useful for one-off meal generation without permanently changing the
 *                  user's profile. Leave null to use whatever's in the DB.
 */
public record MealGenerateRequest(
    String userRequest,
    ProfileSeedRequest profileOverride
) {
}
