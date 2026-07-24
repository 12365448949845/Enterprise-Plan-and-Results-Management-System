package com.planning.platform.auth.vo;

import java.util.List;

public record AuthUserRespVO(
        Long userId,
        String username,
        String realName,
        String mobile,
        Long deptId,
        Long groupId,
        Boolean forceChangePassword,
        List<String> roles,
        List<String> permissions
) {
}
