package com.planning.platform.system.controller;

import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.common.web.ApiResult;
import com.planning.platform.system.model.SystemManagementModels.AuditVO;
import com.planning.platform.system.model.SystemManagementModels.DashboardVO;
import com.planning.platform.system.model.SystemManagementModels.DeptNode;
import com.planning.platform.system.model.SystemManagementModels.DeptSaveReq;
import com.planning.platform.system.model.SystemManagementModels.ImportResult;
import com.planning.platform.system.model.SystemManagementModels.OptionsVO;
import com.planning.platform.system.model.SystemManagementModels.PageResult;
import com.planning.platform.system.model.SystemManagementModels.PermissionNode;
import com.planning.platform.system.model.SystemManagementModels.RegistrationReq;
import com.planning.platform.system.model.SystemManagementModels.RegistrationResult;
import com.planning.platform.system.model.SystemManagementModels.RolePermissionReq;
import com.planning.platform.system.model.SystemManagementModels.RoleSaveReq;
import com.planning.platform.system.model.SystemManagementModels.RoleVO;
import com.planning.platform.system.model.SystemManagementModels.StatusReq;
import com.planning.platform.system.model.SystemManagementModels.UserSaveReq;
import com.planning.platform.system.model.SystemManagementModels.UserVO;
import com.planning.platform.system.model.SystemManagementModels.WorkdayRuleReq;
import com.planning.platform.system.model.SystemManagementModels.WorkdayRuleVO;
import com.planning.platform.system.service.SystemManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/system")
public class SystemManagementController {

    private final SystemManagementService systemManagementService;
    private final AuthService authService;

    @GetMapping("/dashboard")
    public ApiResult<DashboardVO> dashboard(Authentication authentication) {
        return ApiResult.ok(systemManagementService.dashboard(user(authentication)));
    }

    @GetMapping("/options")
    public ApiResult<OptionsVO> options(Authentication authentication) {
        return ApiResult.ok(systemManagementService.options(user(authentication)));
    }

    @PostMapping("/users/register")
    public ApiResult<RegistrationResult> register(Authentication authentication,
                                                   @Valid @RequestBody RegistrationReq request) {
        return ApiResult.ok(systemManagementService.register(user(authentication), request));
    }

    @GetMapping("/users")
    public ApiResult<PageResult<UserVO>> users(Authentication authentication,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Long deptId,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(defaultValue = "1") int pageNo,
                                               @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResult.ok(systemManagementService.userPage(user(authentication), keyword, deptId, status, pageNo, pageSize));
    }

    @PutMapping("/users/{id}")
    public ApiResult<UserVO> updateUser(Authentication authentication, @PathVariable Long id,
                                        @Valid @RequestBody UserSaveReq request) {
        return ApiResult.ok(systemManagementService.updateUser(user(authentication), id, request));
    }

    @PostMapping("/users/{id}/status")
    public ApiResult<Void> userStatus(Authentication authentication, @PathVariable Long id,
                                      @Valid @RequestBody StatusReq request) {
        systemManagementService.changeUserStatus(user(authentication), id, request.status());
        return ApiResult.ok(null);
    }

    @PostMapping("/users/{id}/reset-password")
    public ApiResult<Void> resetPassword(Authentication authentication, @PathVariable Long id) {
        systemManagementService.resetPassword(user(authentication), id);
        return ApiResult.ok(null);
    }

    @PostMapping(value = "/users/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<ImportResult> importUsers(Authentication authentication,
                                                 @RequestPart("file") MultipartFile file) {
        return ApiResult.ok(systemManagementService.importUsers(user(authentication), file));
    }

    @GetMapping("/users/import-template")
    public ResponseEntity<byte[]> employeeImportTemplate(Authentication authentication) {
        return fileResponse("employee-import-template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                systemManagementService.employeeImportTemplate(user(authentication)));
    }

    @GetMapping("/users/export")
    public ResponseEntity<byte[]> exportUsers(Authentication authentication) {
        return csvResponse("employees.csv", systemManagementService.exportUsers(user(authentication)));
    }

    @GetMapping("/departments")
    public ApiResult<List<DeptNode>> departments(Authentication authentication) {
        return ApiResult.ok(systemManagementService.departmentTree(user(authentication)));
    }

    @PostMapping("/departments")
    public ApiResult<DeptNode> createDepartment(Authentication authentication,
                                                @Valid @RequestBody DeptSaveReq request) {
        return ApiResult.ok(systemManagementService.createDepartment(user(authentication), request));
    }

    @PutMapping("/departments/{id}")
    public ApiResult<DeptNode> updateDepartment(Authentication authentication, @PathVariable Long id,
                                                @Valid @RequestBody DeptSaveReq request) {
        return ApiResult.ok(systemManagementService.updateDepartment(user(authentication), id, request));
    }

    @GetMapping("/roles")
    public ApiResult<List<RoleVO>> roles(Authentication authentication) {
        return ApiResult.ok(systemManagementService.roleList(user(authentication)));
    }

    @PostMapping("/roles")
    public ApiResult<RoleVO> createRole(Authentication authentication, @Valid @RequestBody RoleSaveReq request) {
        return ApiResult.ok(systemManagementService.createRole(user(authentication), request));
    }

    @PutMapping("/roles/{id}")
    public ApiResult<RoleVO> updateRole(Authentication authentication, @PathVariable Long id,
                                        @Valid @RequestBody RoleSaveReq request) {
        return ApiResult.ok(systemManagementService.updateRole(user(authentication), id, request));
    }

    @GetMapping("/permissions")
    public ApiResult<List<PermissionNode>> permissions(Authentication authentication) {
        return ApiResult.ok(systemManagementService.permissionTree(user(authentication)));
    }

    @GetMapping("/roles/{id}/permissions")
    public ApiResult<List<Long>> rolePermissions(Authentication authentication, @PathVariable Long id) {
        return ApiResult.ok(systemManagementService.rolePermissionIds(user(authentication), id));
    }

    @PutMapping("/roles/{id}/permissions")
    public ApiResult<Void> saveRolePermissions(Authentication authentication, @PathVariable Long id,
                                               @Valid @RequestBody RolePermissionReq request) {
        systemManagementService.saveRolePermissions(user(authentication), id, request.permissionIds());
        return ApiResult.ok(null);
    }

    @GetMapping("/workday-rules")
    public ApiResult<List<WorkdayRuleVO>> workdayRules(Authentication authentication,
                                                       @RequestParam(required = false) String month,
                                                       @RequestParam(required = false) String ruleType,
                                                       @RequestParam(required = false) Integer status) {
        return ApiResult.ok(systemManagementService.workdayRules(user(authentication), month, ruleType, status));
    }

    @PostMapping("/workday-rules")
    public ApiResult<WorkdayRuleVO> createWorkdayRule(Authentication authentication,
                                                      @Valid @RequestBody WorkdayRuleReq request) {
        return ApiResult.ok(systemManagementService.createWorkdayRule(user(authentication), request));
    }

    @PutMapping("/workday-rules/{id}")
    public ApiResult<WorkdayRuleVO> updateWorkdayRule(Authentication authentication, @PathVariable Long id,
                                                      @Valid @RequestBody WorkdayRuleReq request) {
        return ApiResult.ok(systemManagementService.updateWorkdayRule(user(authentication), id, request));
    }

    @PostMapping("/workday-rules/{id}/status")
    public ApiResult<Void> workdayStatus(Authentication authentication, @PathVariable Long id,
                                         @Valid @RequestBody StatusReq request) {
        systemManagementService.changeWorkdayStatus(user(authentication), id, request.status());
        return ApiResult.ok(null);
    }

    @GetMapping("/audits")
    public ApiResult<PageResult<AuditVO>> audits(Authentication authentication,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                 @RequestParam(required = false) String username,
                                                 @RequestParam(required = false) String action,
                                                 @RequestParam(required = false) String targetType,
                                                 @RequestParam(required = false) String result,
                                                 @RequestParam(defaultValue = "1") int pageNo,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResult.ok(systemManagementService.auditPage(user(authentication), start, end, username,
                action, targetType, result, pageNo, pageSize));
    }

    @GetMapping("/audits/export")
    public ResponseEntity<byte[]> exportAudits(Authentication authentication,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                               @RequestParam(required = false) String username,
                                               @RequestParam(required = false) String action,
                                               @RequestParam(required = false) String targetType,
                                               @RequestParam(required = false) String result) {
        return csvResponse("audit-logs.csv", systemManagementService.exportAudits(user(authentication), start, end,
                username, action, targetType, result));
    }

    private AuthUser user(Authentication authentication) {
        return authService.requireAuthUser(authentication);
    }

    private ResponseEntity<byte[]> csvResponse(String filename, String content) {
        byte[] bytes = ("\ufeff" + content).getBytes(StandardCharsets.UTF_8);
        return fileResponse(filename, "text/csv;charset=UTF-8", bytes);
    }

    private ResponseEntity<byte[]> fileResponse(String filename, String contentType, byte[] bytes) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes);
    }
}
