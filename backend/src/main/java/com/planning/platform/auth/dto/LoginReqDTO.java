package com.planning.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginReqDTO {

    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
