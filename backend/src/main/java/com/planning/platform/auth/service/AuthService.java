package com.planning.platform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.auth.dto.ChangePasswordReqDTO;
import com.planning.platform.auth.dto.LoginReqDTO;
import com.planning.platform.auth.vo.AuthUserRespVO;
import com.planning.platform.auth.vo.TokenRespVO;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.common.security.JwtTokenService;
import com.planning.platform.common.security.SecurityProperties;
import com.planning.platform.system.domain.SysUser;
import com.planning.platform.system.mapper.SysPermissionMapper;
import com.planning.platform.system.mapper.SysRoleMapper;
import com.planning.platform.system.mapper.SysUserMapper;
import com.planning.platform.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final SecurityProperties securityProperties;
    private final AuditLogService auditLogService;

    @Transactional
    public TokenRespVO login(LoginReqDTO request) {
        SysUser user = loadByUsername(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(401, "账号或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BizException(403, "账号已被禁用");
        }
        user.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(user);

        AuthUser authUser = buildAuthUser(user);
        String accessToken = jwtTokenService.createAccessToken(authUser);
        String refreshToken = jwtTokenService.createRefreshToken(authUser);
        return toTokenResp(authUser, accessToken, refreshToken);
    }

    public TokenRespVO refresh(String refreshToken) {
        AuthUser tokenUser = jwtTokenService.parseRefreshToken(refreshToken);
        SysUser user = sysUserMapper.selectById(tokenUser.userId());
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new BizException(401, "登录状态已失效");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BizException(403, "账号已被禁用");
        }
        AuthUser authUser = buildAuthUser(user);
        String accessToken = jwtTokenService.createAccessToken(authUser);
        String nextRefreshToken = jwtTokenService.createRefreshToken(authUser);
        return toTokenResp(authUser, accessToken, nextRefreshToken);
    }

    public AuthUserRespVO me(Authentication authentication) {
        AuthUser authUser = requireAuthUser(authentication);
        SysUser user = sysUserMapper.selectById(authUser.userId());
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new BizException(401, "登录状态已失效");
        }
        return new AuthUserRespVO(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getMobile(),
                user.getDeptId(),
                user.getGroupId(),
                user.getForceChangePassword(),
                authUser.roles(),
                authUser.permissions()
        );
    }

    @Transactional
    public void changePassword(Authentication authentication, ChangePasswordReqDTO request) {
        AuthUser authUser = requireAuthUser(authentication);
        SysUser user = sysUserMapper.selectById(authUser.userId());
        if (user == null || !passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(400, "原密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setForceChangePassword(false);
        sysUserMapper.updateById(user);
        auditLogService.success(authUser, "AUTH_CHANGE_PASSWORD", "SYS_USER", user.getId(), "{\"forceChangePassword\":false}");
    }

    public void logout(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            jwtTokenService.revokeAccessToken(authorization.substring(7));
        }
    }

    public SysUser loadByUsername(String username) {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getDeleted, 0)
                .last("LIMIT 1"));
    }

    public AuthUser buildAuthUser(SysUser user) {
        List<String> roles = sysRoleMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissions = sysPermissionMapper.selectPermissionCodesByUserId(user.getId());
        return new AuthUser(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getDeptId(),
                user.getGroupId(),
                user.getForceChangePassword(),
                roles,
                permissions
        );
    }

    private TokenRespVO toTokenResp(AuthUser authUser, String accessToken, String refreshToken) {
        return new TokenRespVO(
                accessToken,
                refreshToken,
                securityProperties.getAccessTokenMinutes() * 60,
                authUser.userId(),
                authUser.username(),
                authUser.realName(),
                authUser.forceChangePassword(),
                authUser.roles(),
                authUser.permissions()
        );
    }

    public AuthUser requireAuthUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            throw new BizException(401, "请先登录");
        }
        return authUser;
    }
}
