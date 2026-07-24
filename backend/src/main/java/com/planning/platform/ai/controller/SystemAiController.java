package com.planning.platform.ai.controller;

import com.planning.platform.ai.model.AiAdminModels;
import com.planning.platform.ai.service.AiManagementService;
import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.common.web.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/system/ai")
public class SystemAiController {

    private final AuthService authService;
    private final AiManagementService managementService;

    @GetMapping("/configs")
    public ApiResult<List<AiAdminModels.ModelConfigResponse>> configs(Authentication authentication) {
        systemUser(authentication);
        return ApiResult.ok(managementService.configs());
    }

    @PostMapping("/configs")
    public ApiResult<AiAdminModels.ModelConfigResponse> createConfig(Authentication authentication,
                                                                    @Valid @RequestBody AiAdminModels.SaveModelConfigRequest request) {
        return ApiResult.ok(managementService.createConfig(systemUser(authentication), request));
    }

    @PutMapping("/configs/{id}")
    public ApiResult<AiAdminModels.ModelConfigResponse> updateConfig(Authentication authentication,
                                                                    @PathVariable Long id,
                                                                    @Valid @RequestBody AiAdminModels.SaveModelConfigRequest request) {
        return ApiResult.ok(managementService.updateConfig(id, systemUser(authentication), request));
    }

    @PostMapping("/configs/{id}/test")
    public ApiResult<AiAdminModels.TestConnectionResponse> test(Authentication authentication, @PathVariable Long id) {
        systemUser(authentication);
        return ApiResult.ok(managementService.testConnection(id));
    }

    @PostMapping("/configs/{id}/enable")
    public ApiResult<AiAdminModels.ModelConfigResponse> enable(Authentication authentication, @PathVariable Long id) {
        return ApiResult.ok(managementService.enableConfig(id, systemUser(authentication)));
    }

    @GetMapping("/prompts")
    public ApiResult<List<AiAdminModels.PromptResponse>> prompts(Authentication authentication) {
        systemUser(authentication);
        return ApiResult.ok(managementService.prompts());
    }

    @PostMapping("/prompts/publish")
    public ApiResult<AiAdminModels.PromptResponse> publishPrompt(Authentication authentication,
                                                                @Valid @RequestBody AiAdminModels.PublishPromptRequest request) {
        return ApiResult.ok(managementService.publishPrompt(systemUser(authentication), request));
    }

    @GetMapping("/metrics")
    public ApiResult<AiAdminModels.MetricsResponse> metrics(Authentication authentication,
                                                           @RequestParam(defaultValue = "30") int days) {
        systemUser(authentication);
        return ApiResult.ok(managementService.metrics(days));
    }

    private AuthUser systemUser(Authentication authentication) {
        AuthUser user = authService.requireAuthUser(authentication);
        if (user.roles() == null || user.roles().stream().noneMatch(role -> role.equals("SUPER_ADMIN") || role.equals("SYS_ADMIN"))) {
            throw new BizException(403, "当前账号无 AI 系统配置权限");
        }
        return user;
    }
}
