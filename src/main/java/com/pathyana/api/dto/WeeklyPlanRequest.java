package com.pathyana.api.dto;

import java.time.LocalDate;

/**
 * Body for POST /api/meals/weekly/generate.
 *
 * startDate: optional. If null, defaults to today.
 * userRequest: optional free-text "make this week vegetarian" etc.
 */
public record WeeklyPlanRequest(
    LocalDate startDate,
    String userRequest
) {
}
