package com.planning.platform.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DisputeOpinionReqDTO {
    @NotBlank
    private String opinion;
    @NotBlank
    private String comment;
}
