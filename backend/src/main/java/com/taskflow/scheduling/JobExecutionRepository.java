package com.taskflow.scheduling;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobExecutionRepository extends JpaRepository<JobExecution, UUID> {
    Page<JobExecution> findByJobIdOrderByStartedAtDesc(UUID jobId, Pageable pageable);
    long countByJobIdAndStatus(UUID jobId, String status);
}
