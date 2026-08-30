package com.airtribe.tasktracker.notification;

import com.airtribe.tasktracker.common.web.ApiResponse;
import com.airtribe.tasktracker.common.web.PageMeta;
import com.airtribe.tasktracker.notification.dto.NotificationResponse;
import com.airtribe.tasktracker.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<NotificationResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int limit) {
        PageRequest pageRequest = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> result = notificationService.list(principal.getUserId(), pageRequest);
        List<NotificationResponse> data = result.getContent().stream().map(NotificationResponse::from).toList();
        return ApiResponse.ok(data, new PageMeta(page, limit, result.getTotalElements()));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(@AuthenticationPrincipal UserPrincipal principal,
                                                        @PathVariable UUID id) {
        Notification notification = notificationService.markRead(id, principal.getUserId());
        return ApiResponse.ok(NotificationResponse.from(notification));
    }
}
