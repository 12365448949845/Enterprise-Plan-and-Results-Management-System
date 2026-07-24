package com.planning.platform.auth.service;

import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.common.security.JwtTokenService;
import com.planning.platform.common.security.SecurityProperties;
import com.planning.platform.system.domain.SysUser;
import com.planning.platform.system.mapper.SysPermissionMapper;
import com.planning.platform.system.mapper.SysRoleMapper;
import com.planning.platform.system.mapper.SysUserMapper;
import com.planning.platform.system.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysRoleMapper sysRoleMapper;
    @Mock private SysPermissionMapper sysPermissionMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenService jwtTokenService;
    @Mock private SecurityProperties securityProperties;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private AuthService authService;

    @Test
    void refreshRejectsDisabledAccount() {
        AuthUser tokenUser = new AuthUser(9L, "10000009", "员工", 110L, 110L, false,
                List.of("EMPLOYEE"), List.of());
        SysUser user = user(9L, false, 0);
        when(jwtTokenService.parseRefreshToken("refresh-token")).thenReturn(tokenUser);
        when(sysUserMapper.selectById(9L)).thenReturn(user);

        assertThatThrownBy(() -> authService.refresh("refresh-token"))
                .isInstanceOfSatisfying(BizException.class, exception -> assertThat(exception.getCode()).isEqualTo(403));
    }

    @Test
    void refreshUsesCurrentPasswordFlagRolesAndPermissions() {
        AuthUser staleTokenUser = new AuthUser(9L, "10000009", "员工", 110L, 110L, false,
                List.of("EMPLOYEE"), List.of("planning:day:view"));
        SysUser user = user(9L, true, 1);
        when(jwtTokenService.parseRefreshToken("old-refresh")).thenReturn(staleTokenUser);
        when(sysUserMapper.selectById(9L)).thenReturn(user);
        when(sysRoleMapper.selectRoleCodesByUserId(9L)).thenReturn(List.of("EMPLOYEE"));
        when(sysPermissionMapper.selectPermissionCodesByUserId(9L)).thenReturn(List.of("dashboard:view"));
        when(jwtTokenService.createAccessToken(org.mockito.ArgumentMatchers.any())).thenReturn("new-access");
        when(jwtTokenService.createRefreshToken(org.mockito.ArgumentMatchers.any())).thenReturn("new-refresh");
        when(securityProperties.getAccessTokenMinutes()).thenReturn(120L);

        var result = authService.refresh("old-refresh");

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        assertThat(result.forceChangePassword()).isTrue();
        assertThat(result.permissions()).containsExactly("dashboard:view");
    }

    private SysUser user(Long id, boolean forceChangePassword, int status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("10000009");
        user.setRealName("员工");
        user.setDeptId(110L);
        user.setGroupId(110L);
        user.setForceChangePassword(forceChangePassword);
        user.setStatus(status);
        user.setDeleted(0);
        return user;
    }
}
