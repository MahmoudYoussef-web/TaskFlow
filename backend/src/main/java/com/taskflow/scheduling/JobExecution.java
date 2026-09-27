package com.taskflow.scheduling;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_executions")
public class JobExecution {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    protected JobExecution() {
    }

    public JobExecution(UUID jobId, String status) {
        this.jobId = jobId;
        this.status = status;
    }

    public void finish(String status, String errorMessage) {
        this.status = status;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getJobId() { return jobId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
}
