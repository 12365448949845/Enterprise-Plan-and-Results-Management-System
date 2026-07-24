package com.planning.platform.notification.controller;

import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.common.web.ApiResult;
import com.planning.platform.notification.service.UserMessageService;
import com.planning.platform.notification.vo.UserMessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/messages")
public class UserMessageController {

    private final AuthService authService;
    private final UserMessageService messageService;

    @GetMapping("/summary")
    public ApiResult<UserMessageVO.Summary> summary(Authentication authentication) {
        return ApiResult.ok(messageService.summary(user(authentication)));
    }

    @GetMapping
    public ApiResult<UserMessageVO.Page> page(Authentication authentication,
                                              @RequestParam(required = false) String messageType,
                                              @RequestParam(required = false) Boolean unreadOnly,
                                              @RequestParam(defaultValue = "1") int pageNo,
                                              @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResult.ok(messageService.page(user(authentication), messageType, unreadOnly, pageNo, pageSize));
    }

    @PostMapping("/{id}/read")
    public ApiResult<Void> markRead(Authentication authentication, @PathVariable Long id) {
        messageService.markRead(user(authentication), id);
        return ApiResult.ok(null);
    }

    @PostMapping("/read-all")
    public ApiResult<Map<String, Integer>> markAllRead(Authentication authentication) {
        return ApiResult.ok(Map.of("updated", messageService.markAllRead(user(authentication))));
    }

    private AuthUser user(Authentication authentication) {
        return authService.requireAuthUser(authentication);
    }
}
