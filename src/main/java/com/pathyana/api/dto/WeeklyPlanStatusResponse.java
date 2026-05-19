package com.pathyana.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lightweight response for GET /api/meals/weekly/{id}/status.
 *
 * Designed for cheap polling — no embedded meals, just enough to drive a
 * progress bar and decide when to refresh the full plan view.
 */
public record WeeklyPlanStatusResponse(
    Long id,
    String status,           // PENDING, GENERATING, COMPLETED, PARTIAL, FAILED
    LocalDate startDate,
    Integer progressCurrent,
    Integer progressTotal,
    String progressMessage,
    String errorMessage,
    LocalDateTime lastUpdated,
    LocalDateTime completedAt
) {
}
