package com.taskflow.scheduling;

/**
 * Executes the business action behind a job (e.g. send the reminder).
 * Separated so tests can inject a failing handler to prove retry works,
 * without touching worker/lock logic.
 */
public interface JobHandler {
    void handle(ScheduledJob job) throws Exception;
}
