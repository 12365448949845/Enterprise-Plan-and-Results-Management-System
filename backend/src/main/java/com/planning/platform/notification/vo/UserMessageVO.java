package com.planning.platform.notification.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class UserMessageVO {

    private UserMessageVO() {
    }

    public record Item(
            String id,
            String messageType,
            String sceneCode,
            String title,
            String content,
            String status,
            String objectType,
            String objectId,
            String route,
            String menuPath,
            LocalDateTime dueAt,
            LocalDateTime createdAt,
            LocalDateTime readAt,
            boolean overdue
    ) {
    }

    public record Summary(
            long unreadCount,
            long unreadTodoCount,
            long unreadNoticeCount,
            Map<String, Long> menuBadges,
            List<Item> unreadMessages
    ) {
    }

    public record Page(
            List<Item> records,
            long total,
            int pageNo,
            int pageSize,
            long unreadCount
    ) {
    }
}
