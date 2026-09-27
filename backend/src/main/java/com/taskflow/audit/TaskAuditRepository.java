package com.taskflow.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TaskAuditRepository extends JpaRepository<TaskAuditLog, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<TaskAuditLog> {
    Page<TaskAuditLog> findByTaskIdOrderByCreatedAtDesc(UUID taskId, Pageable pageable);
}
