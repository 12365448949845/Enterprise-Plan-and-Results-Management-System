package com.planning.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordReqDTO {

    @NotBlank
    private String oldPassword;

    @NotBlank
    @Size(min = 8, max = 32)
    private String newPassword;
}
