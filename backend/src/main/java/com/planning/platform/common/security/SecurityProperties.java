package com.planning.platform.common.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "planning.security")
public class SecurityProperties {

    @NotBlank
    @Size(min = 32)
    private String jwtSecret;

    @Min(5)
    private long accessTokenMinutes = 120;

    @Min(1)
    private long refreshTokenDays = 7;
}
