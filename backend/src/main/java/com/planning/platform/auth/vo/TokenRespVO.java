package com.planning.platform.auth.vo;

import java.util.List;

public record TokenRespVO(
        String accessToken,
        String refreshToken,
        Long expiresIn,
        Long userId,
        String username,
        String realName,
        Boolean forceChangePassword,
        List<String> roles,
        List<String> permissions
) {
}
