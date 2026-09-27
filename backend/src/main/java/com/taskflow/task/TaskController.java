package com.taskflow.task;

import com.taskflow.auth.CurrentUser;
import com.taskflow.auth.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDtos.TaskResponse create(@CurrentUser User user,
                                        @Valid @RequestBody TaskDtos.CreateTaskRequest req) {
        return TaskDtos.TaskResponse.from(service.create(user, req));
    }

    @GetMapping
    public Page<TaskDtos.TaskResponse> list(@CurrentUser User user,
                                            @RequestParam(required = false) TaskStatus status,
                                            @RequestParam(required = false) UUID assigneeId,
                                            @RequestParam(required = false) String tag,
                                            @RequestParam(required = false) Boolean overdue,
                                            @RequestParam(required = false) String search,
                                            @PageableDefault(size = 50) Pageable pageable) {
        return service.list(user, status, assigneeId, tag, overdue, search, pageable)
                .map(TaskDtos.TaskResponse::from);
    }

    @GetMapping("/{id}")
    public TaskDtos.TaskResponse get(@CurrentUser User user, @PathVariable UUID id) {
        return TaskDtos.TaskResponse.from(service.get(user, id));
    }

    @PutMapping("/{id}")
    public TaskDtos.TaskResponse update(@CurrentUser User user, @PathVariable UUID id,
                                        @Valid @RequestBody TaskDtos.UpdateTaskRequest req) {
        return TaskDtos.TaskResponse.from(service.update(user, id, req));
    }

    @PatchMapping("/{id}/status")
    public TaskDtos.TaskResponse changeStatus(@CurrentUser User user, @PathVariable UUID id,
                                              @Valid @RequestBody TaskDtos.StatusPatchRequest req) {
        return TaskDtos.TaskResponse.from(
                service.changeStatus(user, id, req.status(), req.version()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser User user, @PathVariable UUID id) {
        service.delete(user, id);
    }
}
