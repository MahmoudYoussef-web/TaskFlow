package com.taskflow.audit;

import com.taskflow.auth.CurrentUser;
import com.taskflow.auth.User;
import com.taskflow.task.TaskService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks/{id}/history")
public class AuditController {
    private final TaskService tasks;
    private final TaskAuditRepository audit;

    public AuditController(TaskService tasks, TaskAuditRepository audit) {
        this.tasks = tasks;
        this.audit = audit;
    }

    @GetMapping
    public Page<AuditDto> history(@CurrentUser User user, @PathVariable UUID id,
                                  @PageableDefault(size = 50) Pageable pageable) {
        tasks.get(user, id); // authorization first
        return audit.findByTaskIdOrderByCreatedAtDesc(id, pageable).map(AuditDto::from);
    }

    public record AuditDto(UUID id, String eventType, UUID actorId, String oldValue,
                           String newValue, java.time.Instant createdAt) {
        static AuditDto from(TaskAuditLog l) {
            return new AuditDto(l.getId(), l.getEventType(), l.getActorId(), l.getOldValue(),
                    l.getNewValue(), l.getCreatedAt());
        }
    }
}
