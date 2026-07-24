package com.planning.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenReqDTO {

    @NotBlank
    private String refreshToken;
}
