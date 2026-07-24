package com.planning.platform.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.notification.service.UserMessageService;
import com.planning.platform.system.domain.SysAuditLog;
import com.planning.platform.system.domain.SysDept;
import com.planning.platform.system.domain.SysPermission;
import com.planning.platform.system.domain.SysRole;
import com.planning.platform.system.domain.SysRolePermission;
import com.planning.platform.system.domain.SysUser;
import com.planning.platform.system.domain.SysUserRole;
import com.planning.platform.system.domain.SysWorkdayRule;
import com.planning.platform.system.mapper.SysAuditLogMapper;
import com.planning.platform.system.mapper.SysDeptMapper;
import com.planning.platform.system.mapper.SysPermissionMapper;
import com.planning.platform.system.mapper.SysRoleMapper;
import com.planning.platform.system.mapper.SysRolePermissionMapper;
import com.planning.platform.system.mapper.SysUserMapper;
import com.planning.platform.system.mapper.SysUserRoleMapper;
import com.planning.platform.system.mapper.SysWorkdayRuleMapper;
import com.planning.platform.system.model.SystemManagementModels.AuditVO;
import com.planning.platform.system.model.SystemManagementModels.DashboardVO;
import com.planning.platform.system.model.SystemManagementModels.DeptNode;
import com.planning.platform.system.model.SystemManagementModels.DeptSaveReq;
import com.planning.platform.system.model.SystemManagementModels.ImportResult;
import com.planning.platform.system.model.SystemManagementModels.OptionVO;
import com.planning.platform.system.model.SystemManagementModels.OptionsVO;
import com.planning.platform.system.model.SystemManagementModels.PageResult;
import com.planning.platform.system.model.SystemManagementModels.PermissionNode;
import com.planning.platform.system.model.SystemManagementModels.RegistrationReq;
import com.planning.platform.system.model.SystemManagementModels.RegistrationResult;
import com.planning.platform.system.model.SystemManagementModels.RiskVO;
import com.planning.platform.system.model.SystemManagementModels.RoleSaveReq;
import com.planning.platform.system.model.SystemManagementModels.RoleVO;
import com.planning.platform.system.model.SystemManagementModels.UserSaveReq;
import com.planning.platform.system.model.SystemManagementModels.UserVO;
import com.planning.platform.system.model.SystemManagementModels.WorkdayRuleReq;
import com.planning.platform.system.model.SystemManagementModels.WorkdayRuleVO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemManagementService {

    private static final String INITIAL_PASSWORD = "123456";
    private static final Set<String> ADMIN_ROLES = Set.of("SUPER_ADMIN", "SYS_ADMIN");
    private static final Set<String> LEADER_ROLES = Set.of("DEPT_OWNER", "DEPT_LEADER", "PROJECT_MANAGER", "DIRECT_LEADER");
    private static final Set<String> ORG_TYPES = Set.of("DEPARTMENT", "GROUP", "PROJECT_GROUP");
    private static final Set<String> DATA_SCOPES = Set.of("SELF", "DIRECT_SUBORDINATE", "GROUP", "DEPARTMENT",
            "DEPT_AND_CHILD", "DEPARTMENT_AND_CHILDREN", "ASSIGNED_ORG", "ASSIGNED_CASE", "SYSTEM_CONFIG", "ALL");
    private static final Set<String> WORKDAY_TYPES = Set.of("WORKDAY", "WEEKEND", "HOLIDAY", "LEAVE", "BUSINESS_TRIP", "SPECIAL_SHIFT");

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysWorkdayRuleMapper workdayRuleMapper;
    private final SysAuditLogMapper auditLogMapper;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final UserMessageService messageService;
    private final SecureRandom secureRandom = new SecureRandom();

    public void requireAdmin(AuthUser user) {
        if (user == null || user.roles() == null || user.roles().stream().noneMatch(ADMIN_ROLES::contains)) {
            throw new BizException(403, "当前账号无系统管理权限");
        }
    }

    public DashboardVO dashboard(AuthUser actor) {
        requireAdmin(actor);
        List<SysUser> users = activeOrDisabledUsers();
        List<SysRole> roles = roles();
        List<SysUserRole> userRoles = userRoleMapper.selectList(null);
        Map<Long, SysRole> roleById = roles.stream().collect(Collectors.toMap(SysRole::getId, Function.identity()));
        Set<Long> employeeIds = userRoles.stream()
                .filter(item -> "EMPLOYEE".equals(roleById.getOrDefault(item.getRoleId(), new SysRole()).getCode()))
                .map(SysUserRole::getUserId).collect(Collectors.toSet());

        long missingDept = users.stream().filter(item -> item.getStatus() == 1 && employeeIds.contains(item.getId()) && item.getDeptId() == null).count();
        long missingLeader = users.stream().filter(item -> item.getStatus() == 1 && employeeIds.contains(item.getId()) && item.getDirectLeaderId() == null).count();
        Set<Long> roleIdsWithPermission = rolePermissionMapper.selectList(null).stream()
                .map(SysRolePermission::getRoleId).collect(Collectors.toSet());
        long emptyRoles = roles.stream().filter(item -> item.getStatus() == 1 && !roleIdsWithPermission.contains(item.getId())).count();
        Set<Long> disabledDeptIds = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                        .eq(SysDept::getDeleted, 0).eq(SysDept::getStatus, 0)).stream()
                .map(SysDept::getId).collect(Collectors.toSet());
        long disabledOrgUsers = users.stream().filter(item -> item.getStatus() == 1 && disabledDeptIds.contains(item.getDeptId())).count();

        List<RiskVO> risks = List.of(
                new RiskVO("MISSING_DEPARTMENT", "员工缺少归属组织", missingDept, missingDept > 0 ? "danger" : "success", "/system/employees"),
                new RiskVO("MISSING_LEADER", "员工缺少直属负责人", missingLeader, missingLeader > 0 ? "warning" : "success", "/system/employees"),
                new RiskVO("EMPTY_ROLE", "启用角色尚未配置权限", emptyRoles, emptyRoles > 0 ? "warning" : "success", "/system/permissions"),
                new RiskVO("DISABLED_ORG_USER", "禁用组织仍有关联员工", disabledOrgUsers, disabledOrgUsers > 0 ? "danger" : "success", "/system/orgs")
        );
        syncRiskMessages(actor, risks);

        List<AuditVO> recent = auditLogMapper.selectList(new LambdaQueryWrapper<SysAuditLog>()
                        .orderByDesc(SysAuditLog::getCreatedAt).last("LIMIT 8"))
                .stream().map(this::toAuditVO).toList();
        return new DashboardVO(
                deptMapper.selectCount(new LambdaQueryWrapper<SysDept>().eq(SysDept::getDeleted, 0).eq(SysDept::getStatus, 1)),
                users.stream().filter(item -> item.getStatus() == 1).count(),
                roles.stream().filter(item -> item.getStatus() == 1).count(),
                auditLogMapper.selectCount(new LambdaQueryWrapper<SysAuditLog>().ge(SysAuditLog::getCreatedAt, LocalDateTime.now().minusDays(90))),
                risks,
                recent
        );
    }

    public OptionsVO options(AuthUser actor) {
        requireAdmin(actor);
        List<SysUser> users = activeOrDisabledUsers();
        Map<Long, Set<String>> userRoleCodes = userRoleCodes();
        List<OptionVO> leaders = users.stream()
                .filter(item -> item.getStatus() == 1)
                .filter(item -> userRoleCodes.getOrDefault(item.getId(), Set.of()).stream().anyMatch(LEADER_ROLES::contains))
                .sorted(Comparator.comparing(SysUser::getRealName))
                .map(item -> new OptionVO(item.getId(), item.getRealName(), item.getUsername(), item.getStatus()))
                .toList();
        return new OptionsVO(departmentTree(actor), leaders, roleList(actor));
    }

    @Transactional
    public RegistrationResult register(AuthUser actor, RegistrationReq request) {
        requireAdmin(actor);
        SysDept dept = requireActiveDept(request.deptId());
        SysUser leader = requireActiveUser(request.directLeaderId(), "直属负责人不存在或已禁用");
        ensureMobileAvailable(request.mobile(), null);

        String username = generateUsername();
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setEmployeeNo("E" + username);
        user.setRealName(request.realName().trim());
        user.setMobile(request.mobile().trim());
        user.setDeptId(dept.getId());
        user.setGroupId(isGroup(dept) ? dept.getId() : null);
        user.setDirectLeaderId(leader.getId());
        user.setStatus(1);
        user.setForceChangePassword(true);
        user.setDeleted(0);
        userMapper.insert(user);

        SysRole employeeRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getCode, "EMPLOYEE").eq(SysRole::getDeleted, 0).last("LIMIT 1"));
        if (employeeRole == null) {
            throw new BizException(500, "系统缺少员工角色配置");
        }
        saveUserRole(user.getId(), employeeRole.getId());
        audit(actor, "SYSTEM_EMPLOYEE_REGISTER", "SYS_USER", user.getId(), null, userSnapshot(user));
        return new RegistrationResult(user.getId(), username, user.getEmployeeNo(), INITIAL_PASSWORD,
                user.getRealName(), dept.getName(), leader.getRealName());
    }

    public PageResult<UserVO> userPage(AuthUser actor, String keyword, Long deptId, Integer status,
                                       int pageNo, int pageSize) {
        requireAdmin(actor);
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeleted, 0);
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper.like(SysUser::getRealName, value)
                    .or().like(SysUser::getUsername, value)
                    .or().like(SysUser::getEmployeeNo, value)
                    .or().like(SysUser::getMobile, value));
        }
        if (deptId != null) query.eq(SysUser::getDeptId, deptId);
        if (status != null) query.eq(SysUser::getStatus, status);
        query.orderByDesc(SysUser::getCreatedAt);
        List<SysUser> all = userMapper.selectList(query);
        List<UserVO> records = paginate(all, pageNo, pageSize).stream().map(this::toUserVO).toList();
        return new PageResult<>(records, all.size(), Math.max(1, pageNo), normalizePageSize(pageSize));
    }

    @Transactional
    public UserVO updateUser(AuthUser actor, Long id, UserSaveReq request) {
        requireAdmin(actor);
        SysUser user = requireUser(id);
        Map<String, Object> before = userSnapshot(user);
        SysDept dept = requireActiveDept(request.deptId());
        if (request.directLeaderId() != null && request.directLeaderId().equals(id)) {
            throw new BizException(422, "员工不能成为自己的直属负责人");
        }
        if (request.directLeaderId() != null) requireActiveUser(request.directLeaderId(), "直属负责人不存在或已禁用");
        ensureMobileAvailable(request.mobile(), id);
        List<SysRole> selectedRoles = requireRoles(request.roleIds());
        if (actor.userId().equals(id) && selectedRoles.stream().noneMatch(role -> ADMIN_ROLES.contains(role.getCode()))) {
            throw new BizException(422, "不能撤销自身唯一的系统管理角色");
        }
        if (actor.userId().equals(id) && request.status() != 1) {
            throw new BizException(422, "不能禁用当前登录账号");
        }
        user.setRealName(request.realName().trim());
        user.setMobile(request.mobile().trim());
        user.setDeptId(dept.getId());
        user.setGroupId(isGroup(dept) ? dept.getId() : null);
        user.setDirectLeaderId(request.directLeaderId());
        user.setStatus(request.status());
        userMapper.updateById(user);
        replaceUserRoles(id, request.roleIds());
        audit(actor, "SYSTEM_EMPLOYEE_UPDATE", "SYS_USER", id, before, userSnapshot(user));
        messageService.createSystemNotice(id, "ACCOUNT_SECURITY_NOTICE", "账号信息已更新",
                actor.realName() + "更新了你的组织、直属负责人、角色或账号状态，请确认信息。",
                "SYS_USER", String.valueOf(id), "/", actor.userId());
        return toUserVO(user);
    }

    @Transactional
    public void changeUserStatus(AuthUser actor, Long id, Integer status) {
        requireAdmin(actor);
        if (status == null || (status != 0 && status != 1)) throw new BizException(422, "账号状态不合法");
        if (actor.userId().equals(id) && status == 0) throw new BizException(422, "不能禁用当前登录账号");
        SysUser user = requireUser(id);
        Map<String, Object> before = userSnapshot(user);
        user.setStatus(status);
        userMapper.updateById(user);
        audit(actor, status == 1 ? "SYSTEM_EMPLOYEE_ENABLE" : "SYSTEM_EMPLOYEE_DISABLE", "SYS_USER", id, before, userSnapshot(user));
        messageService.createSystemNotice(id, "ACCOUNT_SECURITY_NOTICE",
                status == 1 ? "账号已启用" : "账号已停用",
                actor.realName() + (status == 1 ? "启用了你的账号。" : "停用了你的账号，如有疑问请联系系统管理员。"),
                "SYS_USER", String.valueOf(id), "/", actor.userId());
    }

    @Transactional
    public void resetPassword(AuthUser actor, Long id) {
        requireAdmin(actor);
        SysUser user = requireUser(id);
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setForceChangePassword(true);
        userMapper.updateById(user);
        audit(actor, "SYSTEM_EMPLOYEE_RESET_PASSWORD", "SYS_USER", id, null,
                Map.of("forceChangePassword", true, "initialPasswordPolicy", "DEFAULT_123456"));
        messageService.createSystemNotice(id, "ACCOUNT_SECURITY_NOTICE", "登录密码已被管理员重置",
                actor.realName() + "重置了你的登录密码，下次登录需要修改初始密码。",
                "SYS_USER", String.valueOf(id), "/change-password", actor.userId());
    }

    @Transactional
    public ImportResult importUsers(AuthUser actor, MultipartFile file) {
        requireAdmin(actor);
        if (file == null || file.isEmpty()) throw new BizException(422, "请选择导入文件");
        List<String> errors = new ArrayList<>();
        int total = 0;
        int success = 0;
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) continue;
                String realName = formatter.formatCellValue(row.getCell(0)).trim();
                String mobile = formatter.formatCellValue(row.getCell(1)).trim();
                String deptValue = formatter.formatCellValue(row.getCell(2)).trim();
                String leaderValue = formatter.formatCellValue(row.getCell(3)).trim();
                if (!StringUtils.hasText(realName) && !StringUtils.hasText(mobile)) continue;
                total++;
                try {
                    register(actor, new RegistrationReq(realName, mobile, Long.parseLong(deptValue), Long.parseLong(leaderValue)));
                    success++;
                } catch (Exception exception) {
                    errors.add("第 " + (index + 1) + " 行：" + exception.getMessage());
                }
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof BizException bizException) throw bizException;
            throw new BizException(422, "导入文件无法解析，请使用系统模板");
        }
        audit(actor, "SYSTEM_EMPLOYEE_IMPORT", "SYS_USER", null, null,
                Map.of("total", total, "success", success, "failed", total - success));
        if (!errors.isEmpty()) {
            messageService.createSystemNotice(actor.userId(), "SYSTEM_IMPORT_RESULT", "员工批量导入存在失败记录",
                    "本次导入成功 " + success + " 条，失败 " + (total - success) + " 条，请查看失败原因。",
                    "SYS_USER_IMPORT", String.valueOf(System.currentTimeMillis()), "/system/employees", actor.userId());
        }
        return new ImportResult(total, success, total - success, errors.stream().limit(30).toList());
    }

    public byte[] employeeImportTemplate(AuthUser actor) {
        requireAdmin(actor);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet employees = workbook.createSheet("员工导入");
            Row header = employees.createRow(0);
            header.createCell(0).setCellValue("姓名");
            header.createCell(1).setCellValue("手机号");
            header.createCell(2).setCellValue("归属组织ID（见可选组织）");
            header.createCell(3).setCellValue("直属负责人ID（见可选负责人）");

            Sheet instructions = workbook.createSheet("使用说明");
            String[] lines = {
                    "1. 请在“员工导入”页从第2行开始填写，每行一名员工。",
                    "2. 归属组织ID和直属负责人ID请从对应的参考页复制。",
                    "3. 系统自动生成8位数字账号，默认密码为123456，首次登录必须改密。",
                    "4. 导入不会发送邀请或开户通知。"
            };
            for (int index = 0; index < lines.length; index++) {
                instructions.createRow(index).createCell(0).setCellValue(lines[index]);
            }

            Sheet organizations = workbook.createSheet("可选组织");
            Row organizationHeader = organizations.createRow(0);
            organizationHeader.createCell(0).setCellValue("组织ID");
            organizationHeader.createCell(1).setCellValue("组织名称");
            organizationHeader.createCell(2).setCellValue("组织编码");
            organizationHeader.createCell(3).setCellValue("组织类型");
            List<SysDept> departments = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                    .eq(SysDept::getDeleted, 0).eq(SysDept::getStatus, 1)
                    .orderByAsc(SysDept::getSortNo).orderByAsc(SysDept::getId));
            for (int index = 0; index < departments.size(); index++) {
                SysDept dept = departments.get(index);
                Row row = organizations.createRow(index + 1);
                row.createCell(0).setCellValue(dept.getId());
                row.createCell(1).setCellValue(dept.getName());
                row.createCell(2).setCellValue(dept.getCode());
                row.createCell(3).setCellValue(dept.getOrgType());
            }

            Sheet leaders = workbook.createSheet("可选负责人");
            Row leaderHeader = leaders.createRow(0);
            leaderHeader.createCell(0).setCellValue("负责人ID");
            leaderHeader.createCell(1).setCellValue("姓名");
            leaderHeader.createCell(2).setCellValue("登录账号");
            Map<Long, Set<String>> roleCodes = userRoleCodes();
            List<SysUser> leaderUsers = activeOrDisabledUsers().stream()
                    .filter(item -> Integer.valueOf(1).equals(item.getStatus()))
                    .filter(item -> roleCodes.getOrDefault(item.getId(), Set.of()).stream().anyMatch(LEADER_ROLES::contains))
                    .sorted(Comparator.comparing(SysUser::getRealName))
                    .toList();
            for (int index = 0; index < leaderUsers.size(); index++) {
                SysUser leader = leaderUsers.get(index);
                Row row = leaders.createRow(index + 1);
                row.createCell(0).setCellValue(leader.getId());
                row.createCell(1).setCellValue(leader.getRealName());
                row.createCell(2).setCellValue(leader.getUsername());
            }

            for (Sheet sheet : List.of(employees, instructions, organizations, leaders)) {
                int columnCount = sheet.getRow(0).getLastCellNum();
                for (int column = 0; column < columnCount; column++) sheet.autoSizeColumn(column);
            }
            workbook.write(output);
            audit(actor, "SYSTEM_EMPLOYEE_IMPORT_TEMPLATE_DOWNLOAD", "SYS_USER", null, null,
                    Map.of("organizationCount", departments.size(), "leaderCount", leaderUsers.size()));
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BizException(500, "员工导入模板生成失败");
        }
    }

    public String exportUsers(AuthUser actor) {
        requireAdmin(actor);
        List<UserVO> users = activeOrDisabledUsers().stream().map(this::toUserVO).toList();
        StringBuilder csv = new StringBuilder("账号,员工编号,姓名,手机号,归属组织,直属负责人,角色,状态,首次改密\r\n");
        for (UserVO user : users) {
            csv.append(csv(user.username())).append(',').append(csv(user.employeeNo())).append(',')
                    .append(csv(user.realName())).append(',').append(csv(user.mobile())).append(',')
                    .append(csv(user.departmentName())).append(',').append(csv(user.directLeaderName())).append(',')
                    .append(csv(String.join("/", user.roleNames()))).append(',')
                    .append(user.status() == 1 ? "启用" : "禁用").append(',')
                    .append(Boolean.TRUE.equals(user.forceChangePassword()) ? "是" : "否").append("\r\n");
        }
        audit(actor, "SYSTEM_EMPLOYEE_EXPORT", "SYS_USER", null, null, Map.of("count", users.size()));
        return csv.toString();
    }

    public List<DeptNode> departmentTree(AuthUser actor) {
        requireAdmin(actor);
        List<SysDept> departments = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getDeleted, 0).orderByAsc(SysDept::getSortNo).orderByAsc(SysDept::getId));
        Map<Long, String> userNames = activeOrDisabledUsers().stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (left, right) -> left));
        Map<Long, Long> employeeCounts = activeOrDisabledUsers().stream().filter(item -> item.getDeptId() != null)
                .collect(Collectors.groupingBy(SysUser::getDeptId, Collectors.counting()));
        Map<Long, List<SysDept>> byParent = departments.stream()
                .collect(Collectors.groupingBy(SysDept::getParentId, LinkedHashMap::new, Collectors.toList()));
        return buildDeptNodes(byParent, 0L, userNames, employeeCounts, new HashSet<>());
    }

    @Transactional
    public DeptNode createDepartment(AuthUser actor, DeptSaveReq request) {
        requireAdmin(actor);
        validateDeptRequest(null, request);
        SysDept dept = new SysDept();
        applyDepartment(dept, request);
        dept.setDeleted(0);
        deptMapper.insert(dept);
        audit(actor, "SYSTEM_ORG_CREATE", "SYS_DEPT", dept.getId(), null, deptSnapshot(dept));
        return findDeptNode(actor, dept.getId());
    }

    @Transactional
    public DeptNode updateDepartment(AuthUser actor, Long id, DeptSaveReq request) {
        requireAdmin(actor);
        SysDept dept = requireDept(id);
        Map<String, Object> before = deptSnapshot(dept);
        validateDeptRequest(id, request);
        if (request.status() == 0 && userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0).eq(SysUser::getStatus, 1).eq(SysUser::getDeptId, id)) > 0) {
            throw new BizException(422, "该组织仍有关联员工，不能直接禁用");
        }
        applyDepartment(dept, request);
        deptMapper.updateById(dept);
        audit(actor, "SYSTEM_ORG_UPDATE", "SYS_DEPT", id, before, deptSnapshot(dept));
        return findDeptNode(actor, id);
    }

    public List<RoleVO> roleList(AuthUser actor) {
        requireAdmin(actor);
        List<SysRole> roles = roles();
        Map<Long, Long> userCounts = userRoleMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(SysUserRole::getRoleId, Collectors.counting()));
        Map<Long, Long> permissionCounts = rolePermissionMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(SysRolePermission::getRoleId, Collectors.counting()));
        return roles.stream().sorted(Comparator.comparing(SysRole::getId))
                .map(role -> new RoleVO(role.getId(), role.getName(), role.getCode(), role.getDescription(),
                        role.getDataScope(), role.getBuiltIn(), role.getStatus(),
                        userCounts.getOrDefault(role.getId(), 0L), permissionCounts.getOrDefault(role.getId(), 0L)))
                .toList();
    }

    @Transactional
    public RoleVO createRole(AuthUser actor, RoleSaveReq request) {
        requireAdmin(actor);
        validateRoleRequest(null, request);
        SysRole role = new SysRole();
        applyRole(role, request);
        role.setBuiltIn(false);
        role.setDeleted(0);
        roleMapper.insert(role);
        audit(actor, "SYSTEM_ROLE_CREATE", "SYS_ROLE", role.getId(), null, roleSnapshot(role));
        return roleList(actor).stream().filter(item -> item.id().equals(role.getId())).findFirst().orElseThrow();
    }

    @Transactional
    public RoleVO updateRole(AuthUser actor, Long id, RoleSaveReq request) {
        requireAdmin(actor);
        SysRole role = requireRole(id);
        Map<String, Object> before = roleSnapshot(role);
        validateRoleRequest(id, request);
        if ("SUPER_ADMIN".equals(role.getCode()) && request.status() == 0) {
            throw new BizException(422, "超级管理员角色不能禁用");
        }
        if (Boolean.TRUE.equals(role.getBuiltIn()) && !role.getCode().equals(request.code())) {
            throw new BizException(422, "内置角色编码不能修改");
        }
        applyRole(role, request);
        roleMapper.updateById(role);
        audit(actor, "SYSTEM_ROLE_UPDATE", "SYS_ROLE", id, before, roleSnapshot(role));
        notifyRoleUsers(actor, id, "角色信息已更新", "管理员更新了你的角色“" + role.getName() + "”，请重新登录确认最新权限。",
                "/");
        return roleList(actor).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    public List<PermissionNode> permissionTree(AuthUser actor) {
        requireAdmin(actor);
        List<SysPermission> permissions = permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getDeleted, 0).orderByAsc(SysPermission::getSortNo).orderByAsc(SysPermission::getId));
        Map<Long, List<SysPermission>> byParent = permissions.stream()
                .collect(Collectors.groupingBy(SysPermission::getParentId, LinkedHashMap::new, Collectors.toList()));
        return buildPermissionNodes(byParent, 0L, new HashSet<>());
    }

    public List<Long> rolePermissionIds(AuthUser actor, Long roleId) {
        requireAdmin(actor);
        requireRole(roleId);
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getRoleId, roleId)).stream()
                .map(SysRolePermission::getPermissionId).toList();
    }

    @Transactional
    public void saveRolePermissions(AuthUser actor, Long roleId, List<Long> permissionIds) {
        requireAdmin(actor);
        SysRole role = requireRole(roleId);
        if ("SUPER_ADMIN".equals(role.getCode())) {
            throw new BizException(422, "超级管理员始终拥有全部权限，无需单独调整");
        }
        Set<Long> requested = new LinkedHashSet<>(permissionIds == null ? List.of() : permissionIds);
        Set<Long> valid = requested.isEmpty() ? Set.of() : permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getDeleted, 0).in(SysPermission::getId, requested)).stream()
                .map(SysPermission::getId).collect(Collectors.toSet());
        if (!valid.equals(requested)) throw new BizException(422, "权限列表包含无效项");
        List<Long> before = rolePermissionIds(actor, roleId);
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
        for (Long permissionId : requested) {
            SysRolePermission relation = new SysRolePermission();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            rolePermissionMapper.insert(relation);
        }
        audit(actor, "SYSTEM_PERMISSION_SAVE", "SYS_ROLE", roleId, Map.of("permissionIds", before), Map.of("permissionIds", requested));
        notifyRoleUsers(actor, roleId, "账号权限已更新", "管理员调整了角色“" + role.getName() + "”的权限，请重新登录确认最新权限。",
                "/");
    }

    public List<WorkdayRuleVO> workdayRules(AuthUser actor, String month, String ruleType, Integer status) {
        requireAdmin(actor);
        LambdaQueryWrapper<SysWorkdayRule> query = new LambdaQueryWrapper<SysWorkdayRule>().eq(SysWorkdayRule::getDeleted, 0);
        if (StringUtils.hasText(month)) {
            try {
                YearMonth yearMonth = YearMonth.parse(month);
                query.between(SysWorkdayRule::getRuleDate, yearMonth.atDay(1), yearMonth.atEndOfMonth());
            } catch (Exception exception) {
                throw new BizException(422, "月份格式必须为 yyyy-MM");
            }
        }
        if (StringUtils.hasText(ruleType)) query.eq(SysWorkdayRule::getRuleType, ruleType);
        if (status != null) query.eq(SysWorkdayRule::getStatus, status);
        return workdayRuleMapper.selectList(query.orderByDesc(SysWorkdayRule::getRuleDate)
                        .orderByDesc(SysWorkdayRule::getVersionNo)).stream()
                .map(this::toWorkdayVO).toList();
    }

    @Transactional
    public WorkdayRuleVO createWorkdayRule(AuthUser actor, WorkdayRuleReq request) {
        requireAdmin(actor);
        validateWorkday(request, null);
        SysWorkdayRule rule = new SysWorkdayRule();
        applyWorkday(rule, request);
        rule.setVersionNo(1);
        rule.setCreatedBy(actor.userId());
        rule.setUpdatedBy(actor.userId());
        rule.setDeleted(0);
        workdayRuleMapper.insert(rule);
        audit(actor, "SYSTEM_WORKDAY_CREATE", "SYS_WORKDAY_RULE", rule.getId(), null, workdaySnapshot(rule));
        if (rule.getStatus() == 1) notifyWorkdayUsers(actor, rule);
        return toWorkdayVO(rule);
    }

    @Transactional
    public WorkdayRuleVO updateWorkdayRule(AuthUser actor, Long id, WorkdayRuleReq request) {
        requireAdmin(actor);
        SysWorkdayRule old = requireWorkday(id);
        validateWorkday(request, id);
        Map<String, Object> before = workdaySnapshot(old);
        old.setStatus(0);
        old.setUpdatedBy(actor.userId());
        workdayRuleMapper.updateById(old);

        SysWorkdayRule next = new SysWorkdayRule();
        applyWorkday(next, request);
        next.setVersionNo(old.getVersionNo() + 1);
        next.setCreatedBy(actor.userId());
        next.setUpdatedBy(actor.userId());
        next.setDeleted(0);
        workdayRuleMapper.insert(next);
        audit(actor, "SYSTEM_WORKDAY_VERSION", "SYS_WORKDAY_RULE", next.getId(), before, workdaySnapshot(next));
        if (next.getStatus() == 1) notifyWorkdayUsers(actor, next);
        return toWorkdayVO(next);
    }

    @Transactional
    public void changeWorkdayStatus(AuthUser actor, Long id, Integer status) {
        requireAdmin(actor);
        if (status == null || (status != 0 && status != 1)) throw new BizException(422, "规则状态不合法");
        SysWorkdayRule rule = requireWorkday(id);
        if (status == 1) ensureNoActiveRule(rule.getRuleDate(), id);
        Map<String, Object> before = workdaySnapshot(rule);
        rule.setStatus(status);
        rule.setUpdatedBy(actor.userId());
        workdayRuleMapper.updateById(rule);
        audit(actor, status == 1 ? "SYSTEM_WORKDAY_ENABLE" : "SYSTEM_WORKDAY_DISABLE", "SYS_WORKDAY_RULE", id,
                before, workdaySnapshot(rule));
        if (status == 1) notifyWorkdayUsers(actor, rule);
    }

    private void syncRiskMessages(AuthUser actor, List<RiskVO> risks) {
        for (RiskVO risk : risks) {
            messageService.syncSystemAlert(actor.userId(), risk.code(), risk.count() > 0,
                    risk.title(), "当前检测到 " + risk.count() + " 项，请进入对应页面处理。",
                    risk.route(), actor.userId());
        }
    }

    private void notifyWorkdayUsers(AuthUser actor, SysWorkdayRule rule) {
        Map<Long, Set<String>> roles = userRoleCodes();
        String reportText = Boolean.TRUE.equals(rule.getForceReport()) ? "需要强制填报日计划" : "无需强制填报日计划";
        for (SysUser user : activeOrDisabledUsers()) {
            if (user.getStatus() != 1 || !roles.getOrDefault(user.getId(), Set.of()).contains("EMPLOYEE")) continue;
            messageService.createSystemNotice(user.getId(), "WORKDAY_RULE_NOTICE", "工作日规则已调整",
                    rule.getRuleDate() + "的工作日规则已更新，" + reportText + "。"
                            + (StringUtils.hasText(rule.getDescription()) ? "说明：" + rule.getDescription() : ""),
                    "SYS_WORKDAY_RULE", String.valueOf(rule.getId()), "/employee/day-plans", actor.userId());
        }
    }

    private void notifyRoleUsers(AuthUser actor, Long roleId, String title, String content, String route) {
        for (SysUserRole relation : userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId))) {
            messageService.createSystemNotice(relation.getUserId(), "ACCOUNT_SECURITY_NOTICE", title, content,
                    "SYS_ROLE", String.valueOf(roleId), route, actor.userId());
        }
    }

    public PageResult<AuditVO> auditPage(AuthUser actor, LocalDateTime start, LocalDateTime end, String username,
                                         String action, String targetType, String result, int pageNo, int pageSize) {
        requireAdmin(actor);
        LambdaQueryWrapper<SysAuditLog> query = auditQuery(start, end, username, action, targetType, result);
        List<SysAuditLog> all = auditLogMapper.selectList(query.orderByDesc(SysAuditLog::getCreatedAt));
        List<AuditVO> records = paginate(all, pageNo, pageSize).stream().map(this::toAuditVO).toList();
        return new PageResult<>(records, all.size(), Math.max(1, pageNo), normalizePageSize(pageSize));
    }

    public String exportAudits(AuthUser actor, LocalDateTime start, LocalDateTime end, String username,
                               String action, String targetType, String result) {
        requireAdmin(actor);
        List<SysAuditLog> logs = auditLogMapper.selectList(auditQuery(start, end, username, action, targetType, result)
                .orderByDesc(SysAuditLog::getCreatedAt).last("LIMIT 5000"));
        StringBuilder csv = new StringBuilder("时间,操作人,动作,对象类型,对象ID,结果,详情\r\n");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (SysAuditLog log : logs) {
            csv.append(csv(log.getCreatedAt() == null ? "" : formatter.format(log.getCreatedAt()))).append(',')
                    .append(csv(log.getUsername())).append(',').append(csv(log.getAction())).append(',')
                    .append(csv(log.getTargetType())).append(',').append(csv(log.getTargetId() == null ? "" : String.valueOf(log.getTargetId()))).append(',')
                    .append(csv(log.getResult())).append(',').append(csv(log.getDetail())).append("\r\n");
        }
        audit(actor, "SYSTEM_AUDIT_EXPORT", "SYS_AUDIT_LOG", null, null, Map.of("count", logs.size()));
        return csv.toString();
    }

    private LambdaQueryWrapper<SysAuditLog> auditQuery(LocalDateTime start, LocalDateTime end, String username,
                                                        String action, String targetType, String result) {
        LambdaQueryWrapper<SysAuditLog> query = new LambdaQueryWrapper<>();
        if (start != null) query.ge(SysAuditLog::getCreatedAt, start);
        if (end != null) query.le(SysAuditLog::getCreatedAt, end);
        if (StringUtils.hasText(username)) query.like(SysAuditLog::getUsername, username.trim());
        if (StringUtils.hasText(action)) query.like(SysAuditLog::getAction, action.trim());
        if (StringUtils.hasText(targetType)) query.eq(SysAuditLog::getTargetType, targetType.trim());
        if (StringUtils.hasText(result)) query.eq(SysAuditLog::getResult, result.trim());
        return query;
    }

    private UserVO toUserVO(SysUser user) {
        Map<Long, SysDept> depts = deptMapper.selectList(new LambdaQueryWrapper<SysDept>().eq(SysDept::getDeleted, 0))
                .stream().collect(Collectors.toMap(SysDept::getId, Function.identity()));
        SysUser leader = user.getDirectLeaderId() == null ? null : userMapper.selectById(user.getDirectLeaderId());
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, user.getId())).stream()
                .map(SysUserRole::getRoleId).toList();
        Map<Long, SysRole> roleById = roles().stream().collect(Collectors.toMap(SysRole::getId, Function.identity()));
        List<String> roleNames = roleIds.stream().map(roleById::get).filter(Objects::nonNull).map(SysRole::getName).toList();
        return new UserVO(user.getId(), user.getUsername(), user.getEmployeeNo(), user.getRealName(), user.getMobile(),
                user.getDeptId(), depts.containsKey(user.getDeptId()) ? depts.get(user.getDeptId()).getName() : "未分配",
                user.getGroupId(), user.getDirectLeaderId(), leader == null ? "未分配" : leader.getRealName(),
                roleIds, roleNames, user.getStatus(), user.getForceChangePassword(), user.getLastLoginAt(), user.getCreatedAt());
    }

    private List<SysUser> activeOrDisabledUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeleted, 0));
    }

    private List<SysRole> roles() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().eq(SysRole::getDeleted, 0));
    }

    private Map<Long, Set<String>> userRoleCodes() {
        Map<Long, String> codes = roles().stream().collect(Collectors.toMap(SysRole::getId, SysRole::getCode));
        Map<Long, Set<String>> result = new HashMap<>();
        for (SysUserRole relation : userRoleMapper.selectList(null)) {
            String code = codes.get(relation.getRoleId());
            if (code != null) result.computeIfAbsent(relation.getUserId(), ignored -> new HashSet<>()).add(code);
        }
        return result;
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) throw new BizException(404, "员工不存在");
        return user;
    }

    private SysUser requireActiveUser(Long id, String message) {
        SysUser user = requireUser(id);
        if (!Integer.valueOf(1).equals(user.getStatus())) throw new BizException(422, message);
        return user;
    }

    private SysDept requireDept(Long id) {
        SysDept dept = deptMapper.selectById(id);
        if (dept == null || Integer.valueOf(1).equals(dept.getDeleted())) throw new BizException(404, "组织节点不存在");
        return dept;
    }

    private SysDept requireActiveDept(Long id) {
        SysDept dept = requireDept(id);
        if (!Integer.valueOf(1).equals(dept.getStatus())) throw new BizException(422, "所选组织已禁用");
        return dept;
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null || Integer.valueOf(1).equals(role.getDeleted())) throw new BizException(404, "角色不存在");
        return role;
    }

    private List<SysRole> requireRoles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BizException(422, "员工至少需要一个角色");
        List<SysRole> selected = roleMapper.selectBatchIds(new LinkedHashSet<>(ids)).stream()
                .filter(role -> !Integer.valueOf(1).equals(role.getDeleted()) && Integer.valueOf(1).equals(role.getStatus())).toList();
        if (selected.size() != new HashSet<>(ids).size()) throw new BizException(422, "角色列表包含无效或已禁用角色");
        return selected;
    }

    private SysWorkdayRule requireWorkday(Long id) {
        SysWorkdayRule rule = workdayRuleMapper.selectById(id);
        if (rule == null || Integer.valueOf(1).equals(rule.getDeleted())) throw new BizException(404, "工作日规则不存在");
        return rule;
    }

    private void ensureMobileAvailable(String mobile, Long ignoredId) {
        SysUser exists = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getMobile, mobile.trim()).eq(SysUser::getDeleted, 0).last("LIMIT 1"));
        if (exists != null && !exists.getId().equals(ignoredId)) throw new BizException(409, "手机号已被其他员工使用");
    }

    private String generateUsername() {
        for (int attempt = 0; attempt < 30; attempt++) {
            String username = String.valueOf(10_000_000 + secureRandom.nextInt(90_000_000));
            if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)) == 0) return username;
        }
        throw new BizException(500, "数字账号生成失败，请重试");
    }

    private void replaceUserRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        for (Long roleId : new LinkedHashSet<>(roleIds)) saveUserRole(userId, roleId);
    }

    private void saveUserRole(Long userId, Long roleId) {
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        userRoleMapper.insert(relation);
    }

    private void validateDeptRequest(Long id, DeptSaveReq request) {
        if (!ORG_TYPES.contains(request.orgType())) throw new BizException(422, "组织类型不合法");
        if (request.status() != 0 && request.status() != 1) throw new BizException(422, "组织状态不合法");
        SysDept duplicate = deptMapper.selectOne(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getCode, request.code().trim()).eq(SysDept::getDeleted, 0).last("LIMIT 1"));
        if (duplicate != null && !duplicate.getId().equals(id)) throw new BizException(409, "组织编码已存在");
        if (request.parentId() != 0) requireDept(request.parentId());
        if (id != null && (id.equals(request.parentId()) || descendants(id).contains(request.parentId()))) {
            throw new BizException(422, "组织父子关系不能形成循环");
        }
        if (request.leaderUserId() != null) requireActiveUser(request.leaderUserId(), "负责人不存在或已禁用");
    }

    private Set<Long> descendants(Long rootId) {
        List<SysDept> all = deptMapper.selectList(new LambdaQueryWrapper<SysDept>().eq(SysDept::getDeleted, 0));
        Map<Long, List<Long>> children = all.stream().collect(Collectors.groupingBy(SysDept::getParentId,
                Collectors.mapping(SysDept::getId, Collectors.toList())));
        Set<Long> result = new HashSet<>();
        List<Long> queue = new ArrayList<>(children.getOrDefault(rootId, List.of()));
        while (!queue.isEmpty()) {
            Long id = queue.remove(0);
            if (result.add(id)) queue.addAll(children.getOrDefault(id, List.of()));
        }
        return result;
    }

    private void applyDepartment(SysDept dept, DeptSaveReq request) {
        dept.setName(request.name().trim());
        dept.setCode(request.code().trim());
        dept.setOrgType(request.orgType());
        dept.setParentId(request.parentId());
        dept.setLeaderUserId(request.leaderUserId());
        dept.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        dept.setStatus(request.status());
    }

    private DeptNode findDeptNode(AuthUser actor, Long id) {
        List<DeptNode> queue = new ArrayList<>(departmentTree(actor));
        while (!queue.isEmpty()) {
            DeptNode node = queue.remove(0);
            if (node.id().equals(id)) return node;
            queue.addAll(node.children());
        }
        throw new BizException(404, "组织节点不存在");
    }

    private List<DeptNode> buildDeptNodes(Map<Long, List<SysDept>> byParent, Long parentId,
                                          Map<Long, String> userNames, Map<Long, Long> employeeCounts, Set<Long> visited) {
        List<DeptNode> result = new ArrayList<>();
        for (SysDept dept : byParent.getOrDefault(parentId, List.of())) {
            if (!visited.add(dept.getId())) continue;
            result.add(new DeptNode(dept.getId(), dept.getParentId(), dept.getName(), dept.getCode(), dept.getOrgType(),
                    dept.getLeaderUserId(), userNames.get(dept.getLeaderUserId()), dept.getSortNo(), dept.getStatus(),
                    employeeCounts.getOrDefault(dept.getId(), 0L), buildDeptNodes(byParent, dept.getId(), userNames, employeeCounts, visited)));
        }
        return result;
    }

    private void validateRoleRequest(Long id, RoleSaveReq request) {
        if (!DATA_SCOPES.contains(request.dataScope())) throw new BizException(422, "数据范围不合法");
        if (request.status() != 0 && request.status() != 1) throw new BizException(422, "角色状态不合法");
        SysRole duplicate = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getCode, request.code().trim()).eq(SysRole::getDeleted, 0).last("LIMIT 1"));
        if (duplicate != null && !duplicate.getId().equals(id)) throw new BizException(409, "角色编码已存在");
    }

    private void applyRole(SysRole role, RoleSaveReq request) {
        role.setName(request.name().trim());
        role.setCode(request.code().trim());
        role.setDescription(request.description());
        role.setDataScope(request.dataScope());
        role.setStatus(request.status());
    }

    private List<PermissionNode> buildPermissionNodes(Map<Long, List<SysPermission>> byParent, Long parentId, Set<Long> visited) {
        List<PermissionNode> result = new ArrayList<>();
        for (SysPermission permission : byParent.getOrDefault(parentId, List.of())) {
            if (!visited.add(permission.getId())) continue;
            result.add(new PermissionNode(permission.getId(), permission.getParentId(), permission.getName(), permission.getCode(),
                    permission.getType(), permission.getPath(), permission.getStatus(),
                    buildPermissionNodes(byParent, permission.getId(), visited)));
        }
        return result;
    }

    private void validateWorkday(WorkdayRuleReq request, Long ignoredId) {
        if (!WORKDAY_TYPES.contains(request.ruleType())) throw new BizException(422, "工作日类型不合法");
        if (request.status() != 0 && request.status() != 1) throw new BizException(422, "规则状态不合法");
        if (request.status() == 1) ensureNoActiveRule(request.ruleDate(), ignoredId);
    }

    private void ensureNoActiveRule(LocalDate date, Long ignoredId) {
        SysWorkdayRule duplicate = workdayRuleMapper.selectOne(new LambdaQueryWrapper<SysWorkdayRule>()
                .eq(SysWorkdayRule::getRuleDate, date).eq(SysWorkdayRule::getStatus, 1)
                .eq(SysWorkdayRule::getDeleted, 0).last("LIMIT 1"));
        if (duplicate != null && !duplicate.getId().equals(ignoredId)) throw new BizException(409, "该日期已存在启用中的规则");
    }

    private void applyWorkday(SysWorkdayRule rule, WorkdayRuleReq request) {
        rule.setRuleDate(request.ruleDate());
        rule.setRuleType(request.ruleType());
        rule.setForceReport(request.forceReport());
        rule.setDescription(request.description());
        rule.setStatus(request.status());
    }

    private WorkdayRuleVO toWorkdayVO(SysWorkdayRule rule) {
        return new WorkdayRuleVO(rule.getId(), rule.getRuleDate(), rule.getRuleType(), rule.getForceReport(),
                rule.getDescription(), rule.getStatus(), rule.getVersionNo(), rule.getUpdatedAt());
    }

    private AuditVO toAuditVO(SysAuditLog log) {
        return new AuditVO(log.getId(), log.getUserId(), log.getUsername(), log.getAction(), log.getTargetType(),
                log.getTargetId(), log.getResult(), log.getClientIp(), log.getDetail(), log.getCreatedAt());
    }

    private boolean isGroup(SysDept dept) {
        return "GROUP".equals(dept.getOrgType()) || "PROJECT_GROUP".equals(dept.getOrgType());
    }

    private <T> List<T> paginate(List<T> values, int pageNo, int pageSize) {
        int size = normalizePageSize(pageSize);
        int page = Math.max(1, pageNo);
        int from = Math.min(values.size(), (page - 1) * size);
        int to = Math.min(values.size(), from + size);
        return values.subList(from, to);
    }

    private int normalizePageSize(int pageSize) {
        return Math.min(100, Math.max(10, pageSize));
    }

    private void audit(AuthUser actor, String action, String targetType, Long targetId, Object before, Object after) {
        try {
            auditLogService.success(actor, action, targetType, targetId,
                    objectMapper.writeValueAsString(Map.of("before", before == null ? Map.of() : before,
                            "after", after == null ? Map.of() : after)));
        } catch (JsonProcessingException exception) {
            auditLogService.success(actor, action, targetType, targetId, "{}");
        }
    }

    private Map<String, Object> userSnapshot(SysUser user) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("username", user.getUsername());
        value.put("employeeNo", user.getEmployeeNo());
        value.put("realName", user.getRealName());
        value.put("mobile", maskMobile(user.getMobile()));
        value.put("deptId", user.getDeptId());
        value.put("groupId", user.getGroupId());
        value.put("directLeaderId", user.getDirectLeaderId());
        value.put("status", user.getStatus());
        return value;
    }

    private Map<String, Object> deptSnapshot(SysDept dept) {
        return mapOfNullable("name", dept.getName(), "code", dept.getCode(), "orgType", dept.getOrgType(),
                "parentId", dept.getParentId(), "leaderUserId", dept.getLeaderUserId(), "status", dept.getStatus());
    }

    private Map<String, Object> roleSnapshot(SysRole role) {
        return mapOfNullable("name", role.getName(), "code", role.getCode(), "description", role.getDescription(),
                "dataScope", role.getDataScope(), "status", role.getStatus());
    }

    private Map<String, Object> workdaySnapshot(SysWorkdayRule rule) {
        return mapOfNullable("ruleDate", rule.getRuleDate(), "ruleType", rule.getRuleType(), "forceReport", rule.getForceReport(),
                "description", rule.getDescription(), "status", rule.getStatus(), "versionNo", rule.getVersionNo());
    }

    private Map<String, Object> mapOfNullable(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) result.put(String.valueOf(values[index]), values[index + 1]);
        return result;
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) return mobile;
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private String csv(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"") ? "\"" + escaped + "\"" : escaped;
    }
}
