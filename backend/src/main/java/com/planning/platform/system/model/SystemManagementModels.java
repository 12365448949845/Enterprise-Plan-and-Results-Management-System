package com.planning.platform.system.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class SystemManagementModels {

    private SystemManagementModels() {
    }

    public record PageResult<T>(List<T> records, long total, long pageNo, long pageSize) {
    }

    public record DashboardVO(long departmentCount, long employeeCount, long roleCount, long auditCount,
                              List<RiskVO> risks, List<AuditVO> recentAudits) {
    }

    public record RiskVO(String code, String title, long count, String level, String route) {
    }

    public record RegistrationReq(
            @NotBlank @Size(max = 80) String realName,
            @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String mobile,
            @NotNull Long deptId,
            @NotNull Long directLeaderId) {
    }

    public record RegistrationResult(Long userId, String username, String employeeNo, String initialPassword,
                                     String realName, String departmentName, String directLeaderName) {
    }

    public record UserSaveReq(
            @NotBlank @Size(max = 80) String realName,
            @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String mobile,
            @NotNull Long deptId,
            Long directLeaderId,
            @NotNull List<Long> roleIds,
            @NotNull Integer status) {
    }

    public record StatusReq(@NotNull Integer status) {
    }

    public record UserVO(Long id, String username, String employeeNo, String realName, String mobile,
                         Long deptId, String departmentName, Long groupId, Long directLeaderId,
                         String directLeaderName, List<Long> roleIds, List<String> roleNames, Integer status,
                         Boolean forceChangePassword, LocalDateTime lastLoginAt, LocalDateTime createdAt) {
    }

    public record ImportResult(int total, int success, int failed, List<String> errors) {
    }

    public record DeptSaveReq(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 50) String code,
            @NotBlank String orgType,
            @NotNull Long parentId,
            Long leaderUserId,
            Integer sortNo,
            @NotNull Integer status) {
    }

    public record DeptNode(Long id, Long parentId, String name, String code, String orgType,
                           Long leaderUserId, String leaderName, Integer sortNo, Integer status,
                           long employeeCount, List<DeptNode> children) {
    }

    public record RoleSaveReq(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$", message = "角色编码必须为大写字母、数字或下划线") String code,
            @Size(max = 255) String description,
            @NotBlank String dataScope,
            @NotNull Integer status) {
    }

    public record RoleVO(Long id, String name, String code, String description, String dataScope,
                         Boolean builtIn, Integer status, long userCount, long permissionCount) {
    }

    public record PermissionNode(Long id, Long parentId, String name, String code, String type, String path,
                                 Integer status, List<PermissionNode> children) {
    }

    public record RolePermissionReq(@NotNull List<Long> permissionIds) {
    }

    public record WorkdayRuleReq(
            @NotNull LocalDate ruleDate,
            @NotBlank String ruleType,
            @NotNull Boolean forceReport,
            @Size(max = 500) String description,
            @NotNull Integer status) {
    }

    public record WorkdayRuleVO(Long id, LocalDate ruleDate, String ruleType, Boolean forceReport,
                                String description, Integer status, Integer versionNo,
                                LocalDateTime updatedAt) {
    }

    public record AuditVO(Long id, Long userId, String username, String action, String targetType,
                          Long targetId, String result, String clientIp, String detail,
                          LocalDateTime createdAt) {
    }

    public record OptionVO(Long id, String label, String secondary, Integer status) {
    }

    public record OptionsVO(List<DeptNode> departments, List<OptionVO> leaders, List<RoleVO> roles) {
    }
}
