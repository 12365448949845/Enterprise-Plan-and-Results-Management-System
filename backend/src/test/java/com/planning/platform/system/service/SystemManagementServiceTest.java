package com.planning.platform.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.notification.service.UserMessageService;
import com.planning.platform.system.domain.SysDept;
import com.planning.platform.system.domain.SysRole;
import com.planning.platform.system.domain.SysUser;
import com.planning.platform.system.domain.SysUserRole;
import com.planning.platform.system.mapper.SysAuditLogMapper;
import com.planning.platform.system.mapper.SysDeptMapper;
import com.planning.platform.system.mapper.SysPermissionMapper;
import com.planning.platform.system.mapper.SysRoleMapper;
import com.planning.platform.system.mapper.SysRolePermissionMapper;
import com.planning.platform.system.mapper.SysUserMapper;
import com.planning.platform.system.mapper.SysUserRoleMapper;
import com.planning.platform.system.mapper.SysWorkdayRuleMapper;
import com.planning.platform.system.model.SystemManagementModels.RegistrationReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemManagementServiceTest {

    @Mock private SysUserMapper userMapper;
    @Mock private SysUserRoleMapper userRoleMapper;
    @Mock private SysDeptMapper deptMapper;
    @Mock private SysRoleMapper roleMapper;
    @Mock private SysPermissionMapper permissionMapper;
    @Mock private SysRolePermissionMapper rolePermissionMapper;
    @Mock private SysWorkdayRuleMapper workdayRuleMapper;
    @Mock private SysAuditLogMapper auditLogMapper;
    @Mock private AuditLogService auditLogService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserMessageService messageService;

    private SystemManagementService service;
    private final AuthUser admin = new AuthUser(1L, "admin", "管理员", 1L, null, false,
            List.of("SUPER_ADMIN"), List.of("system:manage"));

    @BeforeEach
    void setUp() {
        service = new SystemManagementService(userMapper, userRoleMapper, deptMapper, roleMapper,
                permissionMapper, rolePermissionMapper, workdayRuleMapper, auditLogMapper,
                auditLogService, passwordEncoder, new ObjectMapper(), messageService);
    }

    @Test
    void registrationUsesEightDigitAccountAndDefaultPassword() {
        SysDept dept = new SysDept();
        dept.setId(110L);
        dept.setName("产品一组");
        dept.setOrgType("GROUP");
        dept.setStatus(1);
        dept.setDeleted(0);
        when(deptMapper.selectById(110L)).thenReturn(dept);

        SysUser leader = new SysUser();
        leader.setId(20L);
        leader.setRealName("李经理");
        leader.setStatus(1);
        leader.setDeleted(0);
        when(userMapper.selectById(20L)).thenReturn(leader);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-default");
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(99L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));

        SysRole employee = new SysRole();
        employee.setId(4L);
        employee.setCode("EMPLOYEE");
        when(roleMapper.selectOne(any())).thenReturn(employee);

        var result = service.register(admin, new RegistrationReq("张三", "13800009999", 110L, 20L));

        assertTrue(result.username().matches("\\d{8}"));
        assertEquals("E" + result.username(), result.employeeNo());
        assertEquals("123456", result.initialPassword());
        verify(passwordEncoder).encode("123456");
        verify(userRoleMapper).insert(any(SysUserRole.class));
        verify(auditLogService).success(any(), any(), any(), any(), any());
    }

    @Test
    void nonAdminCannotUseSystemManagement() {
        AuthUser employee = new AuthUser(9L, "10000009", "员工", 110L, 110L, false,
                List.of("EMPLOYEE"), List.of());
        BizException exception = assertThrows(BizException.class, () -> service.dashboard(employee));
        assertEquals(403, exception.getCode());
    }

    @Test
    void cannotDisableCurrentAdminAccount() {
        BizException exception = assertThrows(BizException.class,
                () -> service.changeUserStatus(admin, admin.userId(), 0));
        assertEquals(422, exception.getCode());
    }

    @Test
    void superAdminPermissionsAreNotEditable() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setCode("SUPER_ADMIN");
        role.setDeleted(0);
        when(roleMapper.selectById(1L)).thenReturn(role);
        BizException exception = assertThrows(BizException.class,
                () -> service.saveRolePermissions(admin, 1L, List.of()));
        assertEquals(422, exception.getCode());
    }

    @Test
    void regularRoleCanBeSavedWithNoPermissions() {
        SysRole role = new SysRole();
        role.setId(7L);
        role.setCode("SYS_ADMIN");
        role.setDeleted(0);
        when(roleMapper.selectById(7L)).thenReturn(role);

        service.saveRolePermissions(admin, 7L, List.of());

        verify(rolePermissionMapper).delete(any());
        verify(auditLogService).success(any(), any(), any(), any(), any());
    }
}
