package com.planning.platform.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.system.domain.SysUser;
import com.planning.platform.system.domain.SysUserRole;
import com.planning.platform.system.domain.SysDept;
import com.planning.platform.system.mapper.SysDeptMapper;
import com.planning.platform.system.mapper.SysUserMapper;
import com.planning.platform.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;

@Component
@Order(1)
@RequiredArgsConstructor
public class SystemBootstrap implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysDeptMapper sysDeptMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${planning.bootstrap.admin-username:admin}")
    private String adminUsername;

    @Value("${planning.bootstrap.admin-password:Admin@123456}")
    private String adminPassword;

    @Value("${planning.demo-data.enabled:true}")
    private boolean demoDataEnabled;

    @Override
    public void run(String... args) {
        SysUser admin = ensureUser(adminUsername, adminPassword, "ADMIN", "超级管理员",
                "13800000000", 1L, null, null);
        ensureRole(admin.getId(), 1L);

        if (!demoDataEnabled) {
            return;
        }

        SysUser departmentOwner = ensureUser("dept.owner", "Demo@123456", "D001", "产品中心负责人",
                "13800000001", 100L, null, null);
        ensureRole(departmentOwner.getId(), 6L);

        SysUser directLeader = ensureUser("leader", "Demo@123456", "L001", "产品一组直属领导",
                "13800000002", 110L, 110L, departmentOwner.getId());
        ensureRole(directLeader.getId(), 5L);

        SysUser employee = ensureUser("employee", "Demo@123456", "E001", "演示员工张伟",
                "13800000003", 110L, 110L, directLeader.getId());
        ensureRole(employee.getId(), 4L);

        SysUser employeeTwo = ensureUser("employee2", "Demo@123456", "E002", "演示员工李娜",
                "13800000004", 110L, 110L, directLeader.getId());
        ensureRole(employeeTwo.getId(), 4L);

        updateDepartmentLeader(100L, departmentOwner.getId());
        updateDepartmentLeader(110L, directLeader.getId());
    }

    private SysUser ensureUser(String username, String password, String employeeNo, String realName,
                               String mobile, Long deptId, Long groupId, Long directLeaderId) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1"));
        if (user != null) {
            return user;
        }
        user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmployeeNo(employeeNo);
        user.setRealName(realName);
        user.setMobile(mobile);
        user.setDeptId(deptId);
        user.setGroupId(groupId);
        user.setDirectLeaderId(directLeaderId);
        user.setStatus(1);
        user.setForceChangePassword(false);
        user.setDeleted(0);
        sysUserMapper.insert(user);
        return user;
    }

    private void ensureRole(Long userId, Long roleId) {
        Long exists = sysUserRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, roleId));
        if (exists > 0) {
            return;
        }
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        sysUserRoleMapper.insert(userRole);
    }

    private void updateDepartmentLeader(Long deptId, Long leaderUserId) {
        SysDept dept = sysDeptMapper.selectById(deptId);
        if (dept == null || leaderUserId.equals(dept.getLeaderUserId())) {
            return;
        }
        dept.setLeaderUserId(leaderUserId);
        sysDeptMapper.updateById(dept);
    }
}
