package com.taskflow.dashboard;

import com.taskflow.audit.TaskAuditLog;
import com.taskflow.audit.TaskAuditRepository;
import com.taskflow.auth.CurrentUser;
import com.taskflow.auth.User;
import com.taskflow.task.TaskRepository;
import com.taskflow.task.TaskStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Aggregated read model for the overview screen: counts, overdue, recent activity. */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final TaskRepository tasks;
    private final EntityManager em;
    private final TaskAuditRepository audit;

    public DashboardController(TaskRepository tasks, EntityManager em, TaskAuditRepository audit) {
        this.tasks = tasks;
        this.em = em;
        this.audit = audit;
    }

    @GetMapping("/summary")
    public Summary summary(@CurrentUser User user) {
        long todo = tasks.countByOwnerIdAndStatus(user.getId(), TaskStatus.TODO);
        long inProgress = tasks.countByOwnerIdAndStatus(user.getId(), TaskStatus.IN_PROGRESS);
        long done = tasks.countByOwnerIdAndStatus(user.getId(), TaskStatus.DONE);

        var cb = em.getCriteriaBuilder();
        var cq = cb.createQuery(Long.class);
        var root = cq.from(com.taskflow.task.Task.class);
        List<Predicate> preds = new ArrayList<>();
        preds.add(cb.equal(root.get("ownerId"), user.getId()));
        preds.add(cb.isNotNull(root.get("dueDate")));
        preds.add(cb.lessThan(root.get("dueDate"), Instant.now()));
        preds.add(cb.notEqual(root.get("status"), TaskStatus.DONE));
        cq.select(cb.count(root)).where(preds.toArray(new Predicate[0]));
        long overdue = em.createQuery(cq).getSingleResult();

        var recent = audit.findAll(
                (r, q, b) -> b.equal(r.get("actorId"), user.getId()),
                PageRequest.of(0, 10)).map(a -> new Activity(a.getEventType(),
                a.getTaskId().toString(), a.getOldValue(), a.getNewValue(), a.getCreatedAt()));

        return new Summary(todo, inProgress, done, overdue, recent.getContent());
    }

    public record Summary(long todo, long inProgress, long done, long overdue,
                          List<Activity> recentActivity) {
    }

    public record Activity(String eventType, String taskId, String oldValue, String newValue,
                           Instant at) {
    }
}
