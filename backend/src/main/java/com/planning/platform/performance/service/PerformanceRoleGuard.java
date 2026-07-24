package com.planning.platform.performance.service;

import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class PerformanceRoleGuard {

    private static final Set<String> EMPLOYEE_ROLES = Set.of("SUPER_ADMIN", "EMPLOYEE");
    private static final Set<String> LEADER_ROLES = Set.of(
            "SUPER_ADMIN", "DIRECT_LEADER", "PROJECT_MANAGER", "DEPT_LEADER", "DEPT_OWNER"
    );
    private static final Set<String> DEPARTMENT_ROLES = Set.of(
            "SUPER_ADMIN", "DEPT_OWNER", "DEPT_LEADER", "SYS_ADMIN"
    );
    private static final Set<String> EXISTING_ROLES = Set.of(
            "SUPER_ADMIN", "DEPT_OWNER", "DEPT_LEADER",
            "PROJECT_MANAGER", "DIRECT_LEADER", "EMPLOYEE", "REVIEWER"
    );
    private static final Set<String> DISPUTE_MANAGER_ROLES = Set.of(
            "SUPER_ADMIN", "DEPT_OWNER", "DEPT_LEADER"
    );

    public void requireEmployeeModule(AuthUser user) {
        if (!hasAnyRole(user, EMPLOYEE_ROLES)) {
            throw new BizException(403, "当前账号无员工端访问权限");
        }
    }

    public void requireLeaderModule(AuthUser user) {
        if (!hasAnyRole(user, LEADER_ROLES)) {
            throw new BizException(403, "当前账号无直属领导端访问权限");
        }
    }

    public void requireDepartmentModule(AuthUser user) {
        if (!hasAnyRole(user, DEPARTMENT_ROLES)) {
            throw new BizException(403, "当前账号无部门负责人端访问权限");
        }
    }

    /**
     * 裁决端不新增角色：模块访问使用现有角色，案件级授权由评审小组关系继续校验。
     */
    public void requireDisputeModule(AuthUser user) {
        if (!hasAnyRole(user, EXISTING_ROLES)) {
            throw new BizException(403, "当前账号无裁决端访问权限");
        }
    }

    /**
     * 上级/授权管理员复用现有管理角色，不引入 DISPUTE_LEAD。
     */
    public void requireDisputeManager(AuthUser user) {
        if (!hasAnyRole(user, DISPUTE_MANAGER_ROLES)) {
            throw new BizException(403, "当前账号无授权管理裁决权限");
        }
    }

    private boolean hasAnyRole(AuthUser user, Set<String> expectedRoles) {
        return user.roles() != null && user.roles().stream().anyMatch(expectedRoles::contains);
    }
}
