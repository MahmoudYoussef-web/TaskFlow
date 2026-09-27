package com.taskflow.events;

import com.taskflow.auth.CurrentUser;
import com.taskflow.auth.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationRepository notifications;

    public NotificationController(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public Page<NotificationDto> list(@CurrentUser User user,
                                      @PageableDefault(size = 20) Pageable pageable) {
        return notifications.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(NotificationDto::from);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@CurrentUser User user) {
        return Map.of("unread", notifications.countByUserIdAndReadFalse(user.getId()));
    }

    @PostMapping("/{id}/read")
    public NotificationDto markRead(@CurrentUser User user, @PathVariable UUID id) {
        Notification n = notifications.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found."));
        if (!n.getUserId().equals(user.getId())) {
            throw new AccessDeniedException("Not your notification.");
        }
        n.setRead(true);
        return NotificationDto.from(notifications.save(n));
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@CurrentUser User user) {
        notifications.findByUserIdOrderByCreatedAtDesc(user.getId(),
                org.springframework.data.domain.Pageable.unpaged())
                .forEach(n -> n.setRead(true));
    }

    public record NotificationDto(UUID id, String type, String title, String body, boolean read,
                                  Instant createdAt) {
        static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getBody(),
                    n.isRead(), n.getCreatedAt());
        }
    }
}
