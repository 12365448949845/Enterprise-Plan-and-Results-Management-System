package com.planning.platform.common.security;

import java.util.List;

public record AuthUser(
        Long userId,
        String username,
        String realName,
        Long deptId,
        Long groupId,
        Boolean forceChangePassword,
        List<String> roles,
        List<String> permissions
) {
}
