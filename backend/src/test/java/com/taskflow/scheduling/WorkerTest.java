package com.taskflow.scheduling;

import com.taskflow.events.DomainEvent;
import com.taskflow.events.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerTest {

    @Mock ScheduledJobRepository jobs;
    @Mock JobExecutionRepository executions;
    @Mock RedisJobLock lock;
    @Mock JobHandler handler;
    @Mock DomainEventPublisher events;

    Worker worker;

    @BeforeEach
    void setUp() {
        worker = new Worker(jobs, executions, lock, handler, events, 30);
        lenient().when(lock.instanceId()).thenReturn("test-worker");
        lenient().when(executions.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ScheduledJob dueJob(int maxRetries) {
        ScheduledJob job = new ScheduledJob(UUID.randomUUID(), JobType.DUE_DATE_ACTION,
                Instant.now().minusSeconds(60), "key-" + UUID.randomUUID());
        try {
            var f = ScheduledJob.class.getDeclaredField("maxRetries");
            f.setAccessible(true);
            f.setInt(job, maxRetries);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return job;
    }

    @Test
    void failure_retriesWithFutureNextRun() throws Exception {
        ScheduledJob job = dueJob(5);
        when(lock.tryAcquire(job.getId())).thenReturn(true);
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        doThrow(new RuntimeException("smtp down")).when(handler).handle(job);

        worker.process(job.getId());

        assertThat(job.getStatus()).isEqualTo(JobStatus.RETRYING);
        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(job.getNextRunAt()).isAfter(Instant.now());
        assertThat(job.getLastError()).contains("smtp down");
        verify(events, never()).publish(any(DomainEvent.JobTerminallyFailed.class));
    }

    @Test
    void exhaustion_marksPermanentlyFailedAndEmitsEvent() throws Exception {
        ScheduledJob job = dueJob(1);
        when(lock.tryAcquire(job.getId())).thenReturn(true);
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        doThrow(new RuntimeException("boom")).when(handler).handle(job);

        worker.process(job.getId());

        assertThat(job.getStatus()).isEqualTo(JobStatus.PERMANENTLY_FAILED);
        var captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events).publish(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(DomainEvent.JobTerminallyFailed.class);
    }

    @Test
    void lockContended_skipsWithoutExecuting() throws Exception {
        ScheduledJob job = dueJob(5);
        when(lock.tryAcquire(job.getId())).thenReturn(false);

        worker.process(job.getId());

        verify(handler, never()).handle(any());
        verify(executions, never()).save(any());
        verify(jobs, never()).findById(any());
    }

    @Test
    void alreadySucceededJob_isNotReExecuted() {
        ScheduledJob job = dueJob(5);
        job.setStatus(JobStatus.SUCCESS);
        when(lock.tryAcquire(job.getId())).thenReturn(true);
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));

        worker.process(job.getId());

        verify(executions, never()).save(any());
    }
}
