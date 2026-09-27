package com.taskflow.task;

import com.taskflow.auth.User;
import com.taskflow.events.DomainEvent;
import com.taskflow.events.DomainEventPublisher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * User-facing task logic. Knows nothing about ScheduledJobs — it only emits
 * domain events; the scheduling package listens and reacts.
 */
@Service
public class TaskService {
    private final TaskRepository tasks;
    private final EntityManager em;
    private final DomainEventPublisher events;

    public TaskService(TaskRepository tasks, EntityManager em, DomainEventPublisher events) {
        this.tasks = tasks;
        this.em = em;
        this.events = events;
    }

    @Transactional
    public Task create(User actor, TaskDtos.CreateTaskRequest req) {
        Task task = new Task(req.title().trim(), actor.getId());
        task.setDescription(req.description());
        if (req.priority() != null) task.setPriority(req.priority());
        task.setDueDate(req.dueDate());
        if (req.tags() != null) task.setTags(Set.copyOf(req.tags()));
        task.setAssigneeId(req.assigneeId());
        tasks.save(task);
        events.publish(new DomainEvent.TaskCreated(task.getId(), actor.getId(), task.getTitle()));
        if (req.assigneeId() != null) {
            events.publish(new DomainEvent.TaskAssigned(task.getId(), actor.getId(), req.assigneeId()));
        }
        return task;
    }

    @Transactional(readOnly = true)
    public Page<Task> list(User actor, TaskStatus status, UUID assigneeId, String tag,
                           Boolean overdue, String search, Pageable pageable) {
        Specification<Task> spec = (root, query, cb) -> cb.equal(root.get("ownerId"), actor.getId());
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (assigneeId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("assigneeId"), assigneeId));
        }
        if (tag != null && !tag.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.isMember(tag, root.get("tags")));
        }
        if (Boolean.TRUE.equals(overdue)) {
            spec = spec.and((root, query, cb) -> cb.and(
                    cb.isNotNull(root.get("dueDate")),
                    cb.lessThan(root.get("dueDate"), Instant.now()),
                    cb.notEqual(root.get("status"), TaskStatus.DONE)));
        }
        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("description")), like)));
        }
        return tasks.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Task get(User actor, UUID id) {
        Task task = tasks.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Task not found."));
        requireVisible(actor, task);
        return task;
    }

    @Transactional
    public Task update(User actor, UUID id, TaskDtos.UpdateTaskRequest req) {
        Task task = getForWrite(actor, id);
        // Optimistic lock: fail fast with 409 instead of silently losing the other edit.
        if (!Objects.equals(task.getVersion(), req.version())) {
            throw new OptimisticLockException("Stale version");
        }
        TaskStatus oldStatus = task.getStatus();
        UUID oldAssignee = task.getAssigneeId();
        if (req.title() != null) task.setTitle(req.title().trim());
        if (req.description() != null) task.setDescription(req.description());
        if (req.priority() != null) task.setPriority(req.priority());
        if (req.clearDueDate()) task.setDueDate(null);
        else if (req.dueDate() != null) task.setDueDate(req.dueDate());
        if (req.tags() != null) task.setTags(Set.copyOf(req.tags()));
        if (req.assigneeId() != null) task.setAssigneeId(req.assigneeId());
        em.flush();
        events.publish(new DomainEvent.TaskUpdated(task.getId(), actor.getId()));
        emitDiffs(actor, task, oldStatus, oldAssignee);
        return task;
    }

    /** Drag-and-drop endpoint: moves a card between columns. Carries version for conflict safety. */
    @Transactional
    public Task changeStatus(User actor, UUID id, TaskStatus to, Long version) {
        Task task = getForWrite(actor, id);
        if (!Objects.equals(task.getVersion(), version)) {
            throw new OptimisticLockException("Stale version");
        }
        TaskStatus from = task.getStatus();
        if (from != to) {
            task.setStatus(to);
            em.flush();
            events.publish(new DomainEvent.TaskStatusChanged(task.getId(), actor.getId(),
                    from.name(), to.name()));
        }
        return task;
    }

    @Transactional
    public void delete(User actor, UUID id) {
        Task task = getForWrite(actor, id);
        tasks.delete(task);
    }

    private Task getForWrite(User actor, UUID id) {
        Task task = tasks.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Task not found."));
        boolean owner = task.getOwnerId().equals(actor.getId());
        boolean admin = actor.getRole() == com.taskflow.auth.Role.ADMIN;
        boolean assignee = actor.getId().equals(task.getAssigneeId());
        if (!(owner || admin || assignee)) {
            throw new AccessDeniedException("Not your task.");
        }
        return task;
    }

    private void requireVisible(User actor, Task task) {
        boolean owner = task.getOwnerId().equals(actor.getId());
        boolean admin = actor.getRole() == com.taskflow.auth.Role.ADMIN;
        boolean assignee = actor.getId().equals(task.getAssigneeId());
        if (!(owner || admin || assignee)) {
            throw new AccessDeniedException("Not your task.");
        }
    }

    private void emitDiffs(User actor, Task task, TaskStatus oldStatus, UUID oldAssignee) {
        if (task.getStatus() != oldStatus) {
            events.publish(new DomainEvent.TaskStatusChanged(task.getId(), actor.getId(),
                    oldStatus.name(), task.getStatus().name()));
        }
        if (!Objects.equals(task.getAssigneeId(), oldAssignee)
                && task.getAssigneeId() != null) {
            events.publish(new DomainEvent.TaskAssigned(task.getId(), actor.getId(),
                    task.getAssigneeId()));
        }
    }
}
