package com.planning.platform.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.notification.vo.UserMessageVO;
import com.planning.platform.performance.domain.BizTodo;
import com.planning.platform.performance.mapper.BizTodoMapper;
import com.planning.platform.system.domain.SysUser;
import com.planning.platform.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserMessageService {

    private static final Set<String> NOTICE_SCENES = Set.of(
            "MONTH_PLAN_APPROVAL_RESULT", "WEEK_PLAN_APPROVAL_RESULT", "EXPORT_DONE",
            "EXTRA_MONTH_PLAN_ITEM_APPROVAL_RESULT", "DAY_PLAN_COMMENT_RESULT",
            "DAY_PLAN_RISK_NOTICE", "DAY_PLAN_DEPARTMENT_RESULT", "RESULT_FINAL_RESULT",
            "PLAN_ADJUSTMENT_RESULT", "APPEAL_STATUS_RESULT", "DISPUTE_DECISION_RESULT",
            "WORKDAY_RULE_NOTICE", "ACCOUNT_SECURITY_NOTICE", "SYSTEM_RISK_NOTICE",
            "DISPUTE_REVIEWER_ASSIGNED", "DISPUTE_REVIEWER_REMOVED",
            "DISPUTE_PANEL_READY", "DISPUTE_CASE_CREATED"
    );

    private final BizTodoMapper todoMapper;
    private final SysUserMapper userMapper;

    public UserMessageVO.Summary summary(AuthUser user) {
        List<BizTodo> unread = todoMapper.selectList(baseQuery(user.userId())
                .eq(BizTodo::getStatus, "UNREAD")
                .orderByAsc(BizTodo::getDueAt)
                .orderByDesc(BizTodo::getCreatedAt)
                .orderByDesc(BizTodo::getId));
        Map<String, Long> menuBadges = new LinkedHashMap<>();
        long todoCount = 0;
        long noticeCount = 0;
        for (BizTodo item : unread) {
            if ("TODO".equals(messageType(item))) {
                todoCount++;
            } else {
                noticeCount++;
            }
            String menuPath = menuPath(item);
            if (StringUtils.hasText(menuPath)) {
                menuBadges.merge(menuPath, 1L, Long::sum);
            }
        }
        return new UserMessageVO.Summary(unread.size(), todoCount, noticeCount, menuBadges,
                unread.stream().map(this::toItem).toList());
    }

    public UserMessageVO.Page page(AuthUser user, String messageType, Boolean unreadOnly,
                                   int pageNo, int pageSize) {
        int normalizedPageNo = Math.max(1, pageNo);
        int normalizedPageSize = Math.min(100, Math.max(10, pageSize));
        List<BizTodo> all = todoMapper.selectList(baseQuery(user.userId())
                .orderByDesc(BizTodo::getCreatedAt)
                .orderByDesc(BizTodo::getId));
        String normalizedType = StringUtils.hasText(messageType)
                ? messageType.trim().toUpperCase(Locale.ROOT) : null;
        if (normalizedType != null && !Set.of("TODO", "NOTICE", "SYSTEM", "INFO").contains(normalizedType)) {
            throw new BizException(422, "消息类型不合法");
        }
        List<BizTodo> filtered = all.stream()
                .filter(item -> !Boolean.TRUE.equals(unreadOnly) || "UNREAD".equals(item.getStatus()))
                .filter(item -> normalizedType == null
                        || "INFO".equals(normalizedType) && !"TODO".equals(messageType(item))
                        || normalizedType.equals(messageType(item)))
                .toList();
        int from = Math.min(filtered.size(), (normalizedPageNo - 1) * normalizedPageSize);
        int to = Math.min(filtered.size(), from + normalizedPageSize);
        long unreadCount = all.stream().filter(item -> "UNREAD".equals(item.getStatus())).count();
        return new UserMessageVO.Page(filtered.subList(from, to).stream().map(this::toItem).toList(),
                filtered.size(), normalizedPageNo, normalizedPageSize, unreadCount);
    }

    @Transactional
    public void markRead(AuthUser user, Long id) {
        BizTodo item = todoMapper.selectForUpdateById(id);
        requireOwnMessage(user, item);
        if ("UNREAD".equals(item.getStatus())) {
            item.setStatus("READ");
            item.setReadAt(LocalDateTime.now());
            item.setUpdatedBy(user.userId());
            todoMapper.updateById(item);
        }
    }

    @Transactional
    public int markAllRead(AuthUser user) {
        List<BizTodo> unread = todoMapper.selectList(baseQuery(user.userId()).eq(BizTodo::getStatus, "UNREAD"));
        LocalDateTime now = LocalDateTime.now();
        for (BizTodo item : unread) {
            item.setStatus("READ");
            item.setReadAt(now);
            item.setUpdatedBy(user.userId());
            todoMapper.updateById(item);
        }
        return unread.size();
    }

    public void createNotice(Long receiverId, String sceneCode, String title, String content,
                             String objectType, String objectId, String route, Long deptId, Long createdBy) {
        create(receiverId, "NOTICE", sceneCode, title, content, objectType, objectId,
                null, null, null, route, deptId, createdBy);
    }

    public void createNoticeOnce(Long receiverId, String sceneCode, String title, String content,
                                 String objectType, String objectId, String route, Long deptId, Long createdBy) {
        if (receiverId == null) {
            return;
        }
        Long count = todoMapper.selectCount(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(BizTodo::getReceiverId, receiverId)
                .eq(BizTodo::getSceneCode, sceneCode)
                .eq(BizTodo::getObjectType, objectType)
                .eq(BizTodo::getObjectId, objectId));
        if (count == 0) {
            createNotice(receiverId, sceneCode, title, content, objectType, objectId, route, deptId, createdBy);
        }
    }

    public void createSystemNotice(Long receiverId, String sceneCode, String title, String content,
                                   String objectType, String objectId, String route, Long createdBy) {
        create(receiverId, "SYSTEM", sceneCode, title, content, objectType, objectId,
                null, null, null, route, null, createdBy);
    }

    public void createSystemNoticeOnce(Long receiverId, String sceneCode, String title, String content,
                                       String objectType, String objectId, String route, Long createdBy) {
        if (receiverId == null) {
            return;
        }
        Long count = todoMapper.selectCount(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(BizTodo::getReceiverId, receiverId)
                .eq(BizTodo::getSceneCode, sceneCode)
                .eq(BizTodo::getObjectType, objectType)
                .eq(BizTodo::getObjectId, objectId));
        if (count == 0) {
            createSystemNotice(receiverId, sceneCode, title, content, objectType, objectId, route, createdBy);
        }
    }

    @Transactional
    public void syncSystemAlert(Long receiverId, String objectId, boolean active, String title,
                                String content, String route, Long createdBy) {
        if (receiverId == null) {
            return;
        }
        BizTodo item = todoMapper.selectOne(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(BizTodo::getReceiverId, receiverId)
                .eq(BizTodo::getSceneCode, "SYSTEM_RISK_NOTICE")
                .eq(BizTodo::getObjectType, "SYSTEM_RISK")
                .eq(BizTodo::getObjectId, objectId)
                .orderByDesc(BizTodo::getId)
                .last("LIMIT 1"));
        if (!active) {
            if (item != null && !"DONE".equals(item.getStatus())) {
                item.setStatus("DONE");
                item.setUpdatedBy(createdBy);
                todoMapper.updateById(item);
            }
            return;
        }
        if (item == null) {
            createSystemNotice(receiverId, "SYSTEM_RISK_NOTICE", title, content,
                    "SYSTEM_RISK", objectId, route, createdBy);
            return;
        }
        boolean changed = !title.equals(item.getTitle()) || !content.equals(defaultText(item.getTriggerText()));
        item.setTitle(title);
        item.setTriggerText(content);
        item.setRouteHint(route);
        item.setMessageType("SYSTEM");
        if (changed || "DONE".equals(item.getStatus())) {
            item.setStatus("UNREAD");
            item.setReadAt(null);
        }
        item.setUpdatedBy(createdBy);
        todoMapper.updateById(item);
    }

    public void createTodo(Long receiverId, String sceneCode, String title, String content,
                           String objectType, String objectId, LocalDateTime dueAt,
                           String requirement, String impact, String route, Long deptId, Long createdBy) {
        create(receiverId, "TODO", sceneCode, title, content, objectType, objectId,
                dueAt, requirement, impact, route, deptId, createdBy);
    }

    private void create(Long receiverId, String type, String sceneCode, String title, String content,
                        String objectType, String objectId, LocalDateTime dueAt, String requirement,
                        String impact, String route, Long deptId, Long createdBy) {
        if (receiverId == null) {
            return;
        }
        SysUser receiver = userMapper.selectById(receiverId);
        if (receiver == null || Integer.valueOf(1).equals(receiver.getDeleted())) {
            return;
        }
        BizTodo item = new BizTodo();
        item.setSceneCode(sceneCode);
        item.setTitle(title);
        item.setTriggerText(content);
        item.setReceiverId(receiverId);
        item.setReceiverName(receiver.getRealName());
        item.setObjectType(objectType);
        item.setObjectId(objectId);
        item.setDueAt(dueAt);
        item.setRequirementText(requirement);
        item.setImpactText(impact);
        item.setMessageType(type);
        item.setStatus("UNREAD");
        item.setRemindCount(0);
        item.setRouteHint(route);
        item.setDeptId(deptId);
        item.setCreatedBy(createdBy);
        item.setUpdatedBy(createdBy);
        item.setDeleted(0);
        todoMapper.insert(item);
    }

    private LambdaQueryWrapper<BizTodo> baseQuery(Long receiverId) {
        return new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(BizTodo::getReceiverId, receiverId);
    }

    private void requireOwnMessage(AuthUser user, BizTodo item) {
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw new BizException(404, "消息不存在");
        }
        if (!user.userId().equals(item.getReceiverId())) {
            throw new BizException(403, "无权查看该消息");
        }
    }

    private UserMessageVO.Item toItem(BizTodo item) {
        LocalDateTime dueAt = item.getDueAt();
        return new UserMessageVO.Item(String.valueOf(item.getId()), messageType(item), item.getSceneCode(),
                item.getTitle(), defaultText(item.getTriggerText()), item.getStatus(), item.getObjectType(),
                item.getObjectId(), defaultText(item.getRouteHint()), menuPath(item), dueAt,
                item.getCreatedAt(), item.getReadAt(), dueAt != null && dueAt.isBefore(LocalDateTime.now())
                        && !"DONE".equals(item.getStatus()));
    }

    private String messageType(BizTodo item) {
        if (StringUtils.hasText(item.getMessageType())) {
            return item.getMessageType().toUpperCase(Locale.ROOT);
        }
        return NOTICE_SCENES.contains(item.getSceneCode()) ? "NOTICE" : "TODO";
    }

    private String menuPath(BizTodo item) {
        String route = defaultText(item.getRouteHint());
        if (route.startsWith("/employee/month-plans")) return "/employee/month-plans";
        if (route.startsWith("/employee/week-plans")) return "/employee/week-plans";
        if (route.startsWith("/employee/day-plans") || route.startsWith("/employee/daily-plan")) return "/employee/day-plans";
        if (route.startsWith("/employee/results")) return "/employee/results";
        if (route.startsWith("/employee/appeal")) return "/employee/appeals";
        if (route.startsWith("/leader/month-plan-approval")) return "/leader/month-plan-approval";
        if (route.startsWith("/leader/week-plan-approval")) return "/leader/week-plan-approval";
        if (route.startsWith("/leader/daily-review")) return "/leader/daily-review";
        if (route.startsWith("/leader/result-suggest")) return "/leader/result-suggest";
        if (route.startsWith("/leader/extra-task-approval")) return "/leader/extra-task-approval";
        if (route.startsWith("/leader/plan-adjust")) return "/leader/plan-adjust";
        if (route.startsWith("/department/result-confirm")) return "/department/result-confirm";
        if (route.startsWith("/department/export-tasks")) return "/department/export-tasks";
        if (route.startsWith("/department/todo")) return "/department/todo";
        if (route.startsWith("/dispute/cases")) return "/dispute/cases";
        if (route.startsWith("/system/employees") || route.startsWith("/system/users")) return "/system/employees";
        if (route.startsWith("/system/orgs")) return "/system/orgs";
        if (route.startsWith("/system/roles")) return "/system/roles";
        if (route.startsWith("/system/permissions")) return "/system/permissions";
        if (route.startsWith("/system/audits")) return "/system/audits";
        return route.contains("?") ? route.substring(0, route.indexOf('?')) : route;
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }
}
