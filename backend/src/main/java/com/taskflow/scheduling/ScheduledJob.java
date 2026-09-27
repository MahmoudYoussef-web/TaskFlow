package com.taskflow.scheduling;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * System-facing work derived from a {@code Task} with a due date. A task says
 * <i>what</i> is due; this says <i>when the system must act</i> and tracks every
 * attempt until it succeeds or exhausts {@code maxRetries}.
 */
@Entity
@Table(name = "scheduled_jobs")
public class ScheduledJob {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false)
    private JobType jobType;

    @Column(name = "run_at", nullable = false)
    private Instant runAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 5;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /** DB-level idempotency: duplicates are rejected even if code double-dispatches. */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ScheduledJob() {
    }

    public ScheduledJob(UUID taskId, JobType jobType, Instant runAt, String idempotencyKey) {
        this.taskId = taskId;
        this.jobType = jobType;
        this.runAt = runAt;
        this.nextRunAt = runAt;
        this.idempotencyKey = idempotencyKey;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public JobType getJobType() { return jobType; }
    public Instant getRunAt() { return runAt; }
    public void setRunAt(Instant runAt) { this.runAt = runAt; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public int getMaxRetries() { return maxRetries; }
    public Instant getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
