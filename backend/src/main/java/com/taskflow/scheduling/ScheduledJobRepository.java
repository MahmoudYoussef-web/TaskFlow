package com.taskflow.scheduling;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, UUID> {

    @Query("select j from ScheduledJob j where j.nextRunAt <= :now " +
            "and j.status in (com.taskflow.scheduling.JobStatus.PENDING, " +
            "com.taskflow.scheduling.JobStatus.RETRYING) order by j.nextRunAt asc")
    List<ScheduledJob> findDue(Instant now, Pageable pageable);

    Optional<ScheduledJob> findByIdempotencyKey(String idempotencyKey);

    Optional<ScheduledJob> findFirstByTaskIdOrderByCreatedAtDesc(UUID taskId);

    Page<ScheduledJob> findByStatus(JobStatus status, Pageable pageable);
}
