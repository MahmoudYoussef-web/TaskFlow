package com.taskflow.events;

import com.taskflow.audit.TaskAuditLog;
import com.taskflow.audit.TaskAuditRepository;
import com.taskflow.task.Task;
import com.taskflow.task.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Audit + notifications driven purely by domain events. Also notifies the
 * scheduling package (via events only — no direct calls into it).
 */
@Component
public class DomainEventListeners {
    private static final Logger log = LoggerFactory.getLogger(DomainEventListeners.class);

    private final TaskAuditRepository audit;
    private final NotificationRepository notifications;
    private final TaskRepository tasks;

    public DomainEventListeners(TaskAuditRepository audit, NotificationRepository notifications,
                                TaskRepository tasks) {
        this.audit = audit;
        this.notifications = notifications;
        this.tasks = tasks;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTaskCreated(DomainEvent.TaskCreated e) {
        audit.save(new TaskAuditLog(e.taskId(), "TASK_CREATED", e.actorId(), null, e.title()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStatusChanged(DomainEvent.TaskStatusChanged e) {
        audit.save(new TaskAuditLog(e.taskId(), "STATUS_CHANGED", e.actorId(), e.oldStatus(),
                e.newStatus()));
        tasks.findById(e.taskId()).ifPresent(task -> {
            notify(task.getOwnerId(), "STATUS_CHANGED",
                    "Task moved to " + e.newStatus(), task.getTitle());
            if (task.getAssigneeId() != null && !task.getAssigneeId().equals(e.actorId())) {
                notify(task.getAssigneeId(), "STATUS_CHANGED",
                        "Task moved to " + e.newStatus(), task.getTitle());
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAssigned(DomainEvent.TaskAssigned e) {
        audit.save(new TaskAuditLog(e.taskId(), "ASSIGNED", e.actorId(), null,
                e.assigneeId().toString()));
        notify(e.assigneeId(), "ASSIGNED", "A task was assigned to you", taskTitle(e.taskId()));
        log.info("task={} assigned to user={}", e.taskId(), e.assigneeId());
    }

    @EventListener
    @Async
    public void onJobDue(DomainEvent.JobDue e) {
        log.info("job={} for task={} is due — dispatching reminder", e.jobId(), e.taskId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onJobTerminallyFailed(DomainEvent.JobTerminallyFailed e) {
        audit.save(new TaskAuditLog(e.taskId(), "REMINDER_FAILED", null, null, e.lastError()));
        tasks.findById(e.taskId()).ifPresent(task ->
                notify(task.getOwnerId(), "REMINDER_FAILED",
                        "A reminder could not be delivered after all retries", task.getTitle()));
    }

    private String taskTitle(java.util.UUID taskId) {
        return tasks.findById(taskId).map(Task::getTitle).orElse("(deleted task)");
    }

    private void notify(java.util.UUID userId, String type, String title, String body) {
        if (userId != null) {
            notifications.save(new Notification(userId, type, title, body));
        }
    }
}
