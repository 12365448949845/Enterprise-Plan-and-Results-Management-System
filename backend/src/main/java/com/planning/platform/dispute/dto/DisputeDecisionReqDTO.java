package com.planning.platform.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DisputeDecisionReqDTO {
    @NotBlank
    private String decision;
    @NotBlank
    private String comment;
}
