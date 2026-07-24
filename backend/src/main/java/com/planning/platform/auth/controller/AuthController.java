package com.planning.platform.auth.controller;

import com.planning.platform.auth.dto.ChangePasswordReqDTO;
import com.planning.platform.auth.dto.LoginReqDTO;
import com.planning.platform.auth.dto.RefreshTokenReqDTO;
import com.planning.platform.auth.service.AuthService;
import com.planning.platform.auth.vo.AuthUserRespVO;
import com.planning.platform.auth.vo.TokenRespVO;
import com.planning.platform.common.web.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResult<TokenRespVO> login(@Valid @RequestBody LoginReqDTO request) {
        return ApiResult.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResult<TokenRespVO> refresh(@Valid @RequestBody RefreshTokenReqDTO request) {
        return ApiResult.ok(authService.refresh(request.getRefreshToken()));
    }

    @GetMapping("/me")
    public ApiResult<AuthUserRespVO> me(Authentication authentication) {
        return ApiResult.ok(authService.me(authentication));
    }

    @PostMapping("/change-password")
    public ApiResult<Void> changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordReqDTO request) {
        authService.changePassword(authentication, request);
        return ApiResult.ok(null);
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                                  HttpServletRequest request) {
        authService.logout(authorization);
        return ApiResult.ok(null);
    }
}
