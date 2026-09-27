package com.taskflow.scheduling;

import com.taskflow.events.DomainEvent;
import com.taskflow.task.Task;
import com.taskflow.task.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Lives in the SCHEDULING package and only observes task events — this is the
 * entire coupling between the two packages. A task with a due date gains a
 * DUE_DATE_ACTION job; without one, nothing is scheduled.
 */
@Component
public class TaskEventJobSync {
    private static final Logger log = LoggerFactory.getLogger(TaskEventJobSync.class);

    private final TaskRepository tasks;
    private final ScheduledJobRepository jobs;

    public TaskEventJobSync(TaskRepository tasks, ScheduledJobRepository jobs) {
        this.tasks = tasks;
        this.jobs = jobs;
    }

    /** AFTER_COMMIT so the task row is visible to this separate transaction. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTaskCreated(DomainEvent.TaskCreated e) {
        tasks.findById(e.taskId()).ifPresent(this::syncJob);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTaskUpdated(DomainEvent.TaskUpdated e) {
        tasks.findById(e.taskId()).ifPresent(this::syncJob);
    }

    /** Called on updates too (wired from TaskService via status/assigned events is not enough —
     *  so TaskService publishes nothing extra; instead poll for drift is avoided by
     *  exposing syncFor() for the controller path below). */
    @Transactional
    public void syncFor(Task task) {
        syncJob(task);
    }

    private void syncJob(Task task) {
        if (task.getDueDate() == null || task.getStatus() == com.taskflow.task.TaskStatus.DONE) {
            return;
        }
        String key = "task:" + task.getId() + ":due";
        if (jobs.findByIdempotencyKey(key).isPresent()) {
            return; // already scheduled — DB unique constraint backs this up
        }
        try {
            jobs.save(new ScheduledJob(task.getId(), JobType.DUE_DATE_ACTION, task.getDueDate(), key));
            log.info("scheduled due-date job for task={} at {}", task.getId(), task.getDueDate());
        } catch (DataIntegrityViolationException dup) {
            log.info("duplicate job suppressed for task={}", task.getId());
        }
    }
}
