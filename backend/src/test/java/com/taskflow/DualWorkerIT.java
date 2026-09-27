package com.taskflow;

import com.taskflow.scheduling.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * THE proof test: two genuinely parallel Worker instances (separate lock
 * identities, same Redis + same Postgres) race on one due job. Exactly one
 * executes; the other backs off. No mocks — real threads, real lock, real DB.
 */
@SpringBootTest
class DualWorkerIT extends ContainersBase {

    @Autowired ScheduledJobRepository jobs;
    @Autowired JobExecutionRepository executions;
    @Autowired StringRedisTemplate redis;
    @Autowired JobHandler handler;
    @Autowired com.taskflow.events.DomainEventPublisher events;
    @Autowired com.taskflow.auth.UserRepository users;
    @Autowired com.taskflow.task.TaskRepository taskRepo;
    @Autowired org.springframework.transaction.PlatformTransactionManager tm;

    private UUID existingTaskId() {
        var user = users.save(new com.taskflow.auth.User(
                "w" + System.nanoTime() + "@x.com", "hash", "W"));
        return taskRepo.save(new com.taskflow.task.Task("Worker probe", user.getId())).getId();
    }
    org.springframework.transaction.support.TransactionTemplate tx() {
        return new org.springframework.transaction.support.TransactionTemplate(tm);
    }

    @Test
    void twoWorkers_onlyOneExecutes() throws Exception {
        ScheduledJob job = jobs.save(new ScheduledJob(existingTaskId(),
                JobType.DUE_DATE_ACTION, Instant.now().minusSeconds(5),
                "duel-" + UUID.randomUUID()));

        Worker workerA = new Worker(jobs, executions, new RedisJobLock(redis, 120),
                handler, events, 30);
        Worker workerB = new Worker(jobs, executions, new RedisJobLock(redis, 120),
                handler, events, 30);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var fa = pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                tx().executeWithoutResult(s -> workerA.process(job.getId()));
                return null;
            });
            var fb = pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                tx().executeWithoutResult(s -> workerB.process(job.getId()));
                return null;
            });
            start.countDown();
            fa.get(30, TimeUnit.SECONDS);
            fb.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        long successes = executions.countByJobIdAndStatus(job.getId(), "SUCCESS");
        assertThat(successes).isEqualTo(1);

        ScheduledJob done = jobs.findById(job.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(JobStatus.SUCCESS);
    }

    @Test
    void failingJob_retriesThenPermanentlyFails() {
        JobHandler failing = job -> {
            throw new RuntimeException("downstream down");
        };
        Worker worker = new Worker(jobs, executions, new RedisJobLock(redis, 120),
                failing, events, 0);
        ScheduledJob job = new ScheduledJob(existingTaskId(), JobType.DUE_DATE_ACTION,
                Instant.now().minusSeconds(5), "fail-" + UUID.randomUUID());
        try {
            var f = ScheduledJob.class.getDeclaredField("maxRetries");
            f.setAccessible(true);
            f.setInt(job, 2);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        final UUID jobId = jobs.save(job).getId();

        tx().executeWithoutResult(s -> worker.process(jobId));
        ScheduledJob afterFirst = jobs.findById(jobId).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(JobStatus.RETRYING);
        assertThat(afterFirst.getRetryCount()).isEqualTo(1);

        // Simulate backoff elapsing: force next run due, run again → exhausted.
        afterFirst.setNextRunAt(Instant.now().minusSeconds(1));
        jobs.save(afterFirst);
        tx().executeWithoutResult(s -> worker.process(jobId));

        ScheduledJob terminal = jobs.findById(jobId).orElseThrow();
        assertThat(terminal.getStatus()).isEqualTo(JobStatus.PERMANENTLY_FAILED);
        assertThat(executions.countByJobIdAndStatus(jobId, "FAILED")).isEqualTo(2);
    }
}
