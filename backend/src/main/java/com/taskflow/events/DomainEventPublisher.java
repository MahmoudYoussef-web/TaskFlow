package com.taskflow.events;

/** Port for domain-event transport. Spring implementation today; swap for Kafka without touching services. */
public interface DomainEventPublisher {
    void publish(DomainEvent event);
}
