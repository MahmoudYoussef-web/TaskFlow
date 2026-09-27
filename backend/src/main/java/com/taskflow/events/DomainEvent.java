package com.taskflow.events;

import java.time.Instant;
import java.util.UUID;

/** Kafka-ready port: services publish these; the transport is Spring Events today. */
public sealed interface DomainEvent permits DomainEvent.TaskCreated, DomainEvent.TaskUpdated,
        DomainEvent.TaskStatusChanged, DomainEvent.TaskAssigned, DomainEvent.JobDue,
        DomainEvent.JobTerminallyFailed {

    UUID eventId();
    Instant occurredAt();

    record TaskCreated(UUID eventId, Instant occurredAt, UUID taskId, UUID actorId,
                       String title) implements DomainEvent {
        public TaskCreated(UUID taskId, UUID actorId, String title) {
            this(UUID.randomUUID(), Instant.now(), taskId, actorId, title);
        }
    }

    record TaskUpdated(UUID eventId, Instant occurredAt, UUID taskId, UUID actorId) implements DomainEvent {
        public TaskUpdated(UUID taskId, UUID actorId) {
            this(UUID.randomUUID(), Instant.now(), taskId, actorId);
        }
    }

    record TaskStatusChanged(UUID eventId, Instant occurredAt, UUID taskId, UUID actorId,
                             String oldStatus, String newStatus) implements DomainEvent {
        public TaskStatusChanged(UUID taskId, UUID actorId, String oldStatus, String newStatus) {
            this(UUID.randomUUID(), Instant.now(), taskId, actorId, oldStatus, newStatus);
        }
    }

    record TaskAssigned(UUID eventId, Instant occurredAt, UUID taskId, UUID actorId,
                        UUID assigneeId) implements DomainEvent {
        public TaskAssigned(UUID taskId, UUID actorId, UUID assigneeId) {
            this(UUID.randomUUID(), Instant.now(), taskId, actorId, assigneeId);
        }
    }

    record JobDue(UUID eventId, Instant occurredAt, UUID jobId, UUID taskId) implements DomainEvent {
        public JobDue(UUID jobId, UUID taskId) {
            this(UUID.randomUUID(), Instant.now(), jobId, taskId);
        }
    }

    record JobTerminallyFailed(UUID eventId, Instant occurredAt, UUID jobId, UUID taskId,
                               String lastError) implements DomainEvent {
        public JobTerminallyFailed(UUID jobId, UUID taskId, String lastError) {
            this(UUID.randomUUID(), Instant.now(), jobId, taskId, lastError);
        }
    }
}
