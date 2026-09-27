package com.taskflow.scheduling;

import com.taskflow.events.DomainEvent;
import com.taskflow.events.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Default handler: records the reminder as delivered (real delivery is a known limitation). */
@Component
public class ReminderJobHandler implements JobHandler {
    private static final Logger log = LoggerFactory.getLogger(ReminderJobHandler.class);
    private final DomainEventPublisher events;

    public ReminderJobHandler(DomainEventPublisher events) {
        this.events = events;
    }

    @Override
    public void handle(ScheduledJob job) {
        log.info("reminder delivered for task={} job={}", job.getTaskId(), job.getId());
        events.publish(new DomainEvent.JobDue(job.getId(), job.getTaskId()));
    }
}
