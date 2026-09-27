package com.taskflow.scheduling;

import com.taskflow.auth.CurrentUser;
import com.taskflow.auth.Role;
import com.taskflow.auth.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/** Admin/debug view over the scheduling subsystem. */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobAdminController {
    private final ScheduledJobRepository jobs;
    private final JobExecutionRepository executions;
    private final Worker worker;

    public JobAdminController(ScheduledJobRepository jobs, JobExecutionRepository executions,
                              Worker worker) {
        this.jobs = jobs;
        this.executions = executions;
        this.worker = worker;
    }

    @GetMapping
    public Page<JobDto> list(@CurrentUser User user,
                             @RequestParam(required = false) JobStatus status,
                             @PageableDefault(size = 20) Pageable pageable) {
        requireAdmin(user);
        Page<ScheduledJob> page = status == null ? jobs.findAll(pageable)
                : jobs.findByStatus(status, pageable);
        return page.map(JobDto::from);
    }

    @GetMapping("/{id}")
    public JobDto get(@CurrentUser User user, @PathVariable UUID id) {
        requireAdmin(user);
        return JobDto.from(jobs.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job not found.")));
    }

    @GetMapping("/{id}/executions")
    public Page<ExecutionDto> executions(@CurrentUser User user, @PathVariable UUID id,
                                         @PageableDefault(size = 20) Pageable pageable) {
        requireAdmin(user);
        return executions.findByJobIdOrderByStartedAtDesc(id, pageable).map(ExecutionDto::from);
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobDto retry(@CurrentUser User user, @PathVariable UUID id) {
        requireAdmin(user);
        ScheduledJob job = jobs.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job not found."));
        if (job.getStatus() != JobStatus.PERMANENTLY_FAILED
                && job.getStatus() != JobStatus.FAILED) {
            throw new IllegalArgumentException("Only failed jobs can be retried manually.");
        }
        job.setStatus(JobStatus.RETRYING);
        job.setRetryCount(0);
        job.setNextRunAt(Instant.now());
        return JobDto.from(jobs.save(job));
    }

    private static void requireAdmin(User user) {
        if (user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Admin only.");
        }
    }

    public record JobDto(UUID id, UUID taskId, JobType jobType, JobStatus status, int retryCount,
                         int maxRetries, Instant runAt, Instant nextRunAt, String lastError,
                         Instant updatedAt) {
        static JobDto from(ScheduledJob j) {
            return new JobDto(j.getId(), j.getTaskId(), j.getJobType(), j.getStatus(),
                    j.getRetryCount(), j.getMaxRetries(), j.getRunAt(), j.getNextRunAt(),
                    j.getLastError(), j.getUpdatedAt());
        }
    }

    public record ExecutionDto(UUID id, Instant startedAt, Instant finishedAt, String status,
                               String errorMessage) {
        static ExecutionDto from(JobExecution e) {
            return new ExecutionDto(e.getId(), e.getStartedAt(), e.getFinishedAt(),
                    e.getStatus(), e.getErrorMessage());
        }
    }
}
