package com.taskflow.scheduling;

import com.taskflow.common.CorrelationIdFilter;
import com.taskflow.events.DomainEvent;
import com.taskflow.events.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Claims a job via Redis lock → executes idempotently → retries with
 * exponential backoff → PERMANENTLY_FAILED + event on exhaustion.
 * Fail-closed: if the lock can't be acquired, the job is left for another worker.
 */
@Component
public class Worker {
    private static final Logger log = LoggerFactory.getLogger(Worker.class);

    private final ScheduledJobRepository jobs;
    private final JobExecutionRepository executions;
    private final RedisJobLock lock;
    private final JobHandler handler;
    private final DomainEventPublisher events;
    private final long backoffBaseSeconds;

    public Worker(ScheduledJobRepository jobs, JobExecutionRepository executions,
                  RedisJobLock lock, JobHandler handler, DomainEventPublisher events,
                  @Value("${taskflow.worker.backoff-base-seconds:30}") long backoffBaseSeconds) {
        this.jobs = jobs;
        this.executions = executions;
        this.lock = lock;
        this.handler = handler;
        this.events = events;
        this.backoffBaseSeconds = backoffBaseSeconds;
    }

    @Transactional
    public void process(UUID jobId) {
        MDC.put(CorrelationIdFilter.TRACE_ID, "job-" + jobId.toString().substring(0, 8));
        try {
            doProcess(jobId);
        } finally {
            MDC.remove(CorrelationIdFilter.TRACE_ID);
        }
    }

    private void doProcess(UUID jobId) {
        if (!lock.tryAcquire(jobId)) {
            log.info("worker={} skipped job={} — claimed by another worker",
                    lock.instanceId(), jobId);
            return;
        }
        try {
            ScheduledJob job = jobs.findById(jobId).orElse(null);
            if (job == null || !isRunnable(job)) {
                return; // already handled elsewhere — idempotency guard
            }
            job.setStatus(JobStatus.RUNNING);
            JobExecution execution = executions.save(new JobExecution(jobId, "RUNNING"));
            try {
                handler.handle(job);
                job.setStatus(JobStatus.SUCCESS);
                job.setLastError(null);
                execution.finish("SUCCESS", null);
                log.info("worker={} job={} succeeded", lock.instanceId(), jobId);
            } catch (Exception ex) {
                int attempt = job.getRetryCount() + 1;
                job.setRetryCount(attempt);
                job.setLastError(ex.getMessage());
                execution.finish("FAILED", ex.getMessage());
                if (attempt >= job.getMaxRetries()) {
                    job.setStatus(JobStatus.PERMANENTLY_FAILED);
                    events.publish(new DomainEvent.JobTerminallyFailed(jobId, job.getTaskId(),
                            ex.getMessage()));
                    log.warn("worker={} job={} permanently failed after {} attempts",
                            lock.instanceId(), jobId, attempt);
                } else {
                    job.setStatus(JobStatus.RETRYING);
                    job.setNextRunAt(Instant.now().plus(backoff(attempt)));
                    log.info("worker={} job={} failed (attempt {}/{}), next run in {}",
                            lock.instanceId(), jobId, attempt, job.getMaxRetries(),
                            backoff(attempt));
                }
            }
        } finally {
            lock.release(jobId);
        }
    }

    private static boolean isRunnable(ScheduledJob job) {
        return job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.RETRYING;
    }

    Duration backoff(int attempt) {
        long seconds = backoffBaseSeconds * (1L << Math.min(attempt - 1, 6));
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, seconds / 4));
        return Duration.ofSeconds(seconds + jitter);
    }
}
