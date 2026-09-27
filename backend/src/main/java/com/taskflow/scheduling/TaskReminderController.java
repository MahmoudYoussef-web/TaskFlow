package com.taskflow.scheduling;

import com.taskflow.auth.CurrentUser;
import com.taskflow.auth.User;
import com.taskflow.task.TaskService;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Lives in scheduling (which may depend on task, never the reverse).
 * Surfaces the distributed subsystem for a single task: which job backs its
 * due date, what state it's in, and what the last attempt did.
 */
@RestController
@RequestMapping("/api/v1/tasks/{id}/reminder")
public class TaskReminderController {
    private final TaskService tasks;
    private final ScheduledJobRepository jobs;
    private final JobExecutionRepository executions;

    public TaskReminderController(TaskService tasks, ScheduledJobRepository jobs,
                                  JobExecutionRepository executions) {
        this.tasks = tasks;
        this.jobs = jobs;
        this.executions = executions;
    }

    @GetMapping
    public ReminderDto reminder(@CurrentUser User user, @PathVariable UUID id) {
        var task = tasks.get(user, id); // authorization first
        if (task.getDueDate() == null) {
            return new ReminderDto(false, null);
        }
        return new ReminderDto(true,
                jobs.findFirstByTaskIdOrderByCreatedAtDesc(id).map(job -> {
                    var last = executions.findByJobIdOrderByStartedAtDesc(job.getId(),
                            PageRequest.of(0, 1)).stream().findFirst()
                            .map(e -> new Attempt(e.getStatus(), e.getStartedAt(),
                                    e.getFinishedAt(), e.getErrorMessage()))
                            .orElse(null);
                    return new JobState(job.getJobType().name(), job.getStatus().name(),
                            job.getRetryCount(), job.getMaxRetries(), job.getNextRunAt(),
                            job.getLastError(), last);
                }).orElse(null));
    }

    public record ReminderDto(boolean scheduled, JobState job) {
    }

    public record JobState(String type, String status, int retryCount, int maxRetries,
                           Instant nextRunAt, String lastError, Attempt lastAttempt) {
    }

    public record Attempt(String status, Instant startedAt, Instant finishedAt,
                          String errorMessage) {
    }
}
