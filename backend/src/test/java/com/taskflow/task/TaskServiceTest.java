package com.taskflow.task;

import com.taskflow.auth.Role;
import com.taskflow.auth.User;
import com.taskflow.events.DomainEvent;
import com.taskflow.events.DomainEventPublisher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock TaskRepository tasks;
    @Mock EntityManager em;
    @Mock DomainEventPublisher events;

    TaskService service;
    User actor;

    @BeforeEach
    void setUp() {
        service = new TaskService(tasks, em, events);
        actor = new User("a@x.com", "hash", "A");
    }

    @Test
    void create_publishesTaskCreated_withDueDateLeftForScheduler() {
        when(tasks.save(any())).thenAnswer(i -> i.getArgument(0));
        var req = new TaskDtos.CreateTaskRequest("Write docs", "desc", Priority.HIGH,
                java.time.Instant.now().plusSeconds(3600), java.util.Set.of("docs"), null);

        Task task = service.create(actor, req);

        assertThat(task.getTitle()).isEqualTo("Write docs");
        var captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events, atLeastOnce()).publish(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(e ->
                assertThat(e).isInstanceOf(DomainEvent.TaskCreated.class));
    }

    @Test
    void changeStatus_staleVersion_throwsInsteadOfLosingEdits() {
        Task task = new Task("T", actor.getId());
        when(tasks.findById(task.getId())).thenReturn(Optional.of(task));

        // Simulate another edit having bumped the version: pass a wrong one.
        assertThatThrownBy(() ->
                service.changeStatus(actor, task.getId(), TaskStatus.DONE, 999L))
                .isInstanceOf(OptimisticLockException.class);
        verify(events, never()).publish(any());
    }

    @Test
    void changeStatus_sameStatus_emitsNoEvent() {
        Task task = new Task("T", actor.getId());
        when(tasks.findById(task.getId())).thenReturn(Optional.of(task));

        service.changeStatus(actor, task.getId(), TaskStatus.TODO, task.getVersion());

        verify(events, never()).publish(any(DomainEvent.TaskStatusChanged.class));
    }
}
