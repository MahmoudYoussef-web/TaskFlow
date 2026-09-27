package com.taskflow.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Single-flight poller: finds due jobs and hands each to the Worker (which locks first). */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        value = "taskflow.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class JobPoller {
    private static final Logger log = LoggerFactory.getLogger(JobPoller.class);

    private final ScheduledJobRepository jobs;
    private final Worker worker;

    public JobPoller(ScheduledJobRepository jobs, Worker worker) {
        this.jobs = jobs;
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${taskflow.worker.poll-delay-ms:10000}")
    public void poll() {
        var due = jobs.findDue(Instant.now(), PageRequest.of(0, 20));
        if (!due.isEmpty()) {
            log.info("poller found {} due job(s)", due.size());
        }
        due.forEach(job -> {
            try {
                worker.process(job.getId());
            } catch (Exception ex) {
                log.error("worker crashed on job={}", job.getId(), ex);
            }
        });
    }
}
