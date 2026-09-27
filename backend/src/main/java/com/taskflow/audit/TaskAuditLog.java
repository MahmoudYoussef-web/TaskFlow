package com.taskflow.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_audit_log")
public class TaskAuditLog {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected TaskAuditLog() {
    }

    public TaskAuditLog(UUID taskId, String eventType, UUID actorId, String oldValue, String newValue) {
        this.taskId = taskId;
        this.eventType = eventType;
        this.actorId = actorId;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public String getEventType() { return eventType; }
    public UUID getActorId() { return actorId; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public Instant getCreatedAt() { return createdAt; }
}
