package com.planning.platform.planning.service;

import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.performance.service.PerformanceDataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlanningAccessService {

    private final PerformanceDataScopeService dataScopeService;

    public boolean canManage(AuthUser user) {
        return hasRole(user, "SUPER_ADMIN") || hasRole(user, "DEPT_LEADER")
                || hasRole(user, "DEPT_OWNER") || hasRole(user, "PROJECT_MANAGER");
    }

    public boolean canSeeAll(AuthUser user) {
        return canManage(user);
    }

    public void requireManage(AuthUser user) {
        if (!canManage(user)) {
            throw new BizException(403, "当前账号无审批或确认权限");
        }
    }

    public void requireManage(AuthUser user, Long ownerUserId) {
        requireManage(user);
        if (!accessibleOwnerIds(user).contains(ownerUserId)) {
            throw new BizException(403, "该员工不在当前账号的数据范围内");
        }
    }

    public void requireOwner(AuthUser user, Long ownerUserId) {
        if (!user.userId().equals(ownerUserId)) {
            throw new BizException(403, "只能操作本人数据");
        }
    }

    public void requireOwnerOrManager(AuthUser user, Long ownerUserId) {
        if (!accessibleOwnerIds(user).contains(ownerUserId)) {
            throw new BizException(403, "只能操作本人或授权范围内的数据");
        }
    }

    public Set<Long> accessibleOwnerIds(AuthUser user) {
        Set<Long> ownerIds = new HashSet<>();
        ownerIds.add(user.userId());
        if (hasRole(user, "SUPER_ADMIN") || hasRole(user, "DEPT_LEADER") || hasRole(user, "DEPT_OWNER")) {
            ownerIds.addAll(dataScopeService.departmentOwnerIds(user, null));
        } else if (hasRole(user, "PROJECT_MANAGER")) {
            ownerIds.addAll(dataScopeService.leaderOwnerIds(user, null));
        }
        return ownerIds;
    }

    private boolean hasRole(AuthUser user, String role) {
        return user.roles() != null && user.roles().contains(role);
    }
}
