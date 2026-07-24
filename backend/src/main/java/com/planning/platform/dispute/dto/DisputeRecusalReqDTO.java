package com.planning.platform.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DisputeRecusalReqDTO {
    @NotBlank
    private String reason;
}
