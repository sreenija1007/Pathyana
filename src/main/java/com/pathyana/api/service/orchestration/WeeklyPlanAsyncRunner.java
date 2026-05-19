package com.pathyana.api.service.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Tiny indirection bean that exists ONLY to enable @Async for the weekly plan
 * generator.
 *
 * Why this class exists:
 *   Spring's @Async works by wrapping the bean in a proxy. The proxy only
 *   intercepts calls made FROM OUTSIDE the class — never self-calls within
 *   the same class. So if WeeklyPlanOrchestratorService.createAndStart() calls
 *   this.generateAsync(), the @Async annotation is silently ignored and the
 *   work runs on the calling thread (the HTTP request thread).
 *
 *   This was the bug that caused POST /api/meals/weekly/generate to hang for
 *   5-6 minutes instead of returning immediately.
 *
 * How this fix works:
 *   We move the @Async entry point to a SEPARATE bean. createAndStart() now
 *   calls asyncRunner.run(...), which is a cross-bean call — so Spring's
 *   proxy correctly intercepts and dispatches to a background thread.
 *
 * Why we use @Lazy:
 *   WeeklyPlanOrchestratorService injects this class, and this class injects
 *   WeeklyPlanOrchestratorService back. Without @Lazy on one side, Spring
 *   refuses to start due to a circular dependency. @Lazy breaks the cycle.
 */
@Component
public class WeeklyPlanAsyncRunner {

    private static final Logger log = LoggerFactory.getLogger(WeeklyPlanAsyncRunner.class);

    private final WeeklyPlanOrchestratorService orchestrator;

    public WeeklyPlanAsyncRunner(@Lazy WeeklyPlanOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * The actual background entry point. Because this method lives on a
     * different bean than the caller, Spring's @Async proxy intercepts the call
     * and dispatches it to a background TaskExecutor thread.
     */
    @Async
    public void run(Long weeklyPlanId, Long userId) {
        log.info("Async runner dispatched generation for plan {} on thread {}",
            weeklyPlanId, Thread.currentThread().getName());
        try {
            orchestrator.generate(weeklyPlanId, userId);
        } catch (Exception e) {
            log.error("Weekly plan {} async generation failed unrecoverably", weeklyPlanId, e);
        }
    }
}
