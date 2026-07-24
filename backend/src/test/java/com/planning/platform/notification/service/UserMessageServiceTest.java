package com.planning.platform.notification.service;

import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.performance.domain.BizTodo;
import com.planning.platform.performance.mapper.BizTodoMapper;
import com.planning.platform.system.domain.SysUser;
import com.planning.platform.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserMessageServiceTest {

    @Mock private BizTodoMapper todoMapper;
    @Mock private SysUserMapper userMapper;
    @InjectMocks private UserMessageService service;

    private final AuthUser user = new AuthUser(10L, "employee", "员工甲", 110L, null,
            false, List.of("EMPLOYEE"), List.of());

    @Test
    void summaryReturnsOnlyUnreadCountsAndNormalizedMenuBadges() {
        BizTodo approval = todo(1L, "TODO", "MONTH_PLAN_APPROVAL", "/leader/month-plan-approval", "UNREAD");
        BizTodo result = todo(2L, "NOTICE", "MONTH_PLAN_APPROVAL_RESULT", "/employee/month-plans/99", "UNREAD");
        when(todoMapper.selectList(any())).thenReturn(List.of(approval, result));

        var summary = service.summary(user);

        assertThat(summary.unreadCount()).isEqualTo(2);
        assertThat(summary.unreadTodoCount()).isEqualTo(1);
        assertThat(summary.unreadNoticeCount()).isEqualTo(1);
        assertThat(summary.menuBadges()).containsEntry("/leader/month-plan-approval", 1L)
                .containsEntry("/employee/month-plans", 1L);
    }

    @Test
    void markReadUpdatesOnlyOwnUnreadMessage() {
        BizTodo item = todo(1L, "NOTICE", "RESULT_FINAL_RESULT", "/employee/results", "UNREAD");
        item.setReceiverId(user.userId());
        when(todoMapper.selectForUpdateById(1L)).thenReturn(item);

        service.markRead(user, 1L);

        assertThat(item.getStatus()).isEqualTo("READ");
        assertThat(item.getReadAt()).isNotNull();
        verify(todoMapper).updateById(item);
    }

    @Test
    void markReadRejectsAnotherUsersMessage() {
        BizTodo item = todo(1L, "NOTICE", "RESULT_FINAL_RESULT", "/employee/results", "UNREAD");
        item.setReceiverId(99L);
        when(todoMapper.selectForUpdateById(1L)).thenReturn(item);

        BizException error = catchThrowableOfType(() -> service.markRead(user, 1L), BizException.class);

        assertThat(error.getCode()).isEqualTo(403);
    }

    @Test
    void createNoticePersistsRecipientAndNoticeType() {
        SysUser receiver = new SysUser();
        receiver.setId(10L);
        receiver.setRealName("员工甲");
        receiver.setDeleted(0);
        when(userMapper.selectById(10L)).thenReturn(receiver);

        service.createNotice(10L, "DAY_PLAN_COMMENT_RESULT", "日计划已点评", "请查看点评意见",
                "DAY_PLAN_RESULT", "8", "/employee/day-plans", 110L, 20L);

        ArgumentCaptor<BizTodo> captor = ArgumentCaptor.forClass(BizTodo.class);
        verify(todoMapper).insert(captor.capture());
        assertThat(captor.getValue().getMessageType()).isEqualTo("NOTICE");
        assertThat(captor.getValue().getReceiverId()).isEqualTo(10L);
        assertThat(captor.getValue().getStatus()).isEqualTo("UNREAD");
    }

    private BizTodo todo(Long id, String type, String scene, String route, String status) {
        BizTodo item = new BizTodo();
        item.setId(id);
        item.setMessageType(type);
        item.setSceneCode(scene);
        item.setTitle("消息");
        item.setTriggerText("内容");
        item.setReceiverId(10L);
        item.setObjectType("OBJECT");
        item.setObjectId(String.valueOf(id));
        item.setRouteHint(route);
        item.setStatus(status);
        item.setCreatedAt(LocalDateTime.now());
        item.setDeleted(0);
        return item;
    }
}
