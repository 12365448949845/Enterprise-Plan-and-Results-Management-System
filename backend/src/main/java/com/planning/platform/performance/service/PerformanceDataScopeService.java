package com.planning.platform.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.performance.vo.PerformanceVO.OrgNodeVO;
import com.planning.platform.system.domain.SysDept;
import com.planning.platform.system.domain.SysUser;
import com.planning.platform.system.mapper.SysDeptMapper;
import com.planning.platform.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PerformanceDataScopeService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    public List<SysDept> activeDepartments() {
        return deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getDeleted, 0)
                .eq(SysDept::getStatus, 1)
                .orderByAsc(SysDept::getSortNo)
                .orderByAsc(SysDept::getId));
    }

    public List<SysUser> activeUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
    }

    public Map<Long, SysDept> departmentMap() {
        Map<Long, SysDept> result = new HashMap<>();
        activeDepartments().forEach(dept -> result.put(dept.getId(), dept));
        return result;
    }

    public Map<Long, SysUser> userMap() {
        Map<Long, SysUser> result = new HashMap<>();
        activeUsers().forEach(user -> result.put(user.getId(), user));
        return result;
    }

    public SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new BizException(404, "用户不存在");
        }
        return user;
    }

    public void lockUser(Long userId) {
        if (userId == null || userMapper.selectIdForUpdate(userId) == null) {
            throw new BizException(404, "用户不存在");
        }
    }

    public String departmentName(Long deptId) {
        if (deptId == null) {
            return "未分配部门";
        }
        SysDept dept = deptMapper.selectById(deptId);
        return dept == null ? String.valueOf(deptId) : dept.getName();
    }

    public Set<Long> leaderOwnerIds(AuthUser user, Long requestedDeptId) {
        List<SysUser> users = activeUsers();
        Set<Long> allowedDeptIds = allowedLeaderDepartments(user);
        if (requestedDeptId != null) {
            Set<Long> requestedScope = departmentScope(requestedDeptId);
            allowedDeptIds.retainAll(requestedScope);
        }
        Set<Long> result = new HashSet<>();
        for (SysUser candidate : users) {
            if (candidate.getId().equals(user.userId())) {
                continue;
            }
            boolean visible = hasRole(user, "SUPER_ADMIN")
                    || user.userId().equals(candidate.getDirectLeaderId())
                    || hasRole(user, "PROJECT_MANAGER") && user.groupId() != null && user.groupId().equals(candidate.getGroupId())
                    || isDepartmentLeader(user) && allowedDeptIds.contains(candidate.getDeptId());
            if (visible && (requestedDeptId == null || allowedDeptIds.contains(candidate.getDeptId()))) {
                result.add(candidate.getId());
            }
        }
        return result;
    }

    public Set<Long> departmentOwnerIds(AuthUser user, Long requestedDeptId) {
        Set<Long> allowedDeptIds = allowedDepartmentScope(user);
        if (requestedDeptId != null) {
            Set<Long> requestedScope = departmentScope(requestedDeptId);
            if (!hasRole(user, "SUPER_ADMIN") && requestedScope.stream().noneMatch(allowedDeptIds::contains)) {
                throw new BizException(403, "所选组织不在当前账号的数据范围内");
            }
            allowedDeptIds.retainAll(requestedScope);
        }
        Set<Long> result = new HashSet<>();
        for (SysUser candidate : activeUsers()) {
            if (allowedDeptIds.contains(candidate.getDeptId())) {
                result.add(candidate.getId());
            }
        }
        return result;
    }

    public void requireLeaderOwner(AuthUser user, Long ownerUserId) {
        if (!leaderOwnerIds(user, null).contains(ownerUserId)) {
            throw new BizException(403, "该员工不在当前直属领导的数据范围内");
        }
    }

    public void requireDepartmentOwner(AuthUser user, Long ownerUserId) {
        if (!departmentOwnerIds(user, null).contains(ownerUserId)) {
            throw new BizException(403, "该员工不在当前部门负责人的数据范围内");
        }
    }

    public void requireLeaderOrg(AuthUser user, Long deptId) {
        if (deptId != null && !allowedLeaderDepartments(user).contains(deptId)) {
            throw new BizException(403, "所选组织不在当前直属领导的数据范围内");
        }
    }

    public void requireDepartmentOrg(AuthUser user, Long deptId) {
        if (deptId != null && !allowedDepartmentScope(user).contains(deptId)) {
            throw new BizException(403, "所选组织不在当前部门负责人的数据范围内");
        }
    }

    public List<OrgNodeVO> orgTree(AuthUser user, boolean departmentModule) {
        Set<Long> visible = new HashSet<>(departmentModule ? allowedDepartmentScope(user) : allowedLeaderDepartments(user));
        if (hasRole(user, "SUPER_ADMIN")) {
            activeDepartments().forEach(dept -> visible.add(dept.getId()));
        }
        Map<Long, List<SysDept>> children = new HashMap<>();
        for (SysDept dept : activeDepartments()) {
            children.computeIfAbsent(dept.getParentId(), ignored -> new ArrayList<>()).add(dept);
        }
        List<OrgNodeVO> roots = new ArrayList<>();
        for (SysDept dept : activeDepartments()) {
            if (!visible.contains(dept.getId()) || visible.contains(dept.getParentId())) {
                continue;
            }
            roots.add(toNode(dept, children, visible));
        }
        return roots;
    }

    public Long directLeaderId(Long ownerUserId) {
        SysUser owner = requireUser(ownerUserId);
        if (owner.getDirectLeaderId() != null) {
            return owner.getDirectLeaderId();
        }
        SysDept dept = owner.getDeptId() == null ? null : deptMapper.selectById(owner.getDeptId());
        return dept == null ? null : dept.getLeaderUserId();
    }

    public Long departmentLeaderId(Long deptId) {
        Map<Long, SysDept> departments = departmentMap();
        Long currentId = deptId;
        while (currentId != null && currentId != 0L) {
            SysDept dept = departments.get(currentId);
            if (dept == null) {
                break;
            }
            if (dept.getLeaderUserId() != null) {
                return dept.getLeaderUserId();
            }
            currentId = dept.getParentId();
        }
        return null;
    }

    public Long departmentOwnerId(Long deptId) {
        Map<Long, SysDept> departments = departmentMap();
        SysDept current = departments.get(deptId);
        if (current == null) {
            return null;
        }
        Long fallback = current.getLeaderUserId();
        Long parentId = current.getParentId();
        while (parentId != null && parentId != 0L) {
            SysDept parent = departments.get(parentId);
            if (parent == null) {
                break;
            }
            if (parent.getLeaderUserId() != null) {
                return parent.getLeaderUserId();
            }
            parentId = parent.getParentId();
        }
        return fallback;
    }

    public Set<Long> departmentScope(Long rootDeptId) {
        Set<Long> result = new HashSet<>();
        if (rootDeptId == null) {
            return result;
        }
        result.add(rootDeptId);
        boolean changed;
        do {
            changed = false;
            for (SysDept dept : activeDepartments()) {
                if (result.contains(dept.getParentId()) && result.add(dept.getId())) {
                    changed = true;
                }
            }
        } while (changed);
        return result;
    }

    private Set<Long> allowedLeaderDepartments(AuthUser user) {
        if (hasRole(user, "SUPER_ADMIN")) {
            Set<Long> all = new HashSet<>();
            activeDepartments().forEach(dept -> all.add(dept.getId()));
            return all;
        }
        if (isDepartmentLeader(user)) {
            return departmentScope(user.deptId());
        }
        Set<Long> result = new HashSet<>();
        for (SysUser candidate : activeUsers()) {
            if (user.userId().equals(candidate.getDirectLeaderId())
                    || hasRole(user, "PROJECT_MANAGER") && user.groupId() != null && user.groupId().equals(candidate.getGroupId())) {
                if (candidate.getDeptId() != null) {
                    result.add(candidate.getDeptId());
                }
            }
        }
        return result;
    }

    private Set<Long> allowedDepartmentScope(AuthUser user) {
        if (hasRole(user, "SUPER_ADMIN")) {
            Set<Long> all = new HashSet<>();
            activeDepartments().forEach(dept -> all.add(dept.getId()));
            return all;
        }
        return departmentScope(user.deptId());
    }

    private OrgNodeVO toNode(SysDept dept, Map<Long, List<SysDept>> children, Set<Long> visible) {
        List<OrgNodeVO> childNodes = children.getOrDefault(dept.getId(), List.of()).stream()
                .filter(child -> visible.contains(child.getId()))
                .map(child -> toNode(child, children, visible))
                .toList();
        return new OrgNodeVO(dept.getId(), dept.getName(), childNodes.isEmpty() ? "GROUP" : "DEPARTMENT", childNodes);
    }

    private boolean isDepartmentLeader(AuthUser user) {
        return hasRole(user, "DEPT_LEADER") || hasRole(user, "DEPT_OWNER");
    }

    private boolean hasRole(AuthUser user, String role) {
        return user.roles() != null && user.roles().contains(role);
    }
}
