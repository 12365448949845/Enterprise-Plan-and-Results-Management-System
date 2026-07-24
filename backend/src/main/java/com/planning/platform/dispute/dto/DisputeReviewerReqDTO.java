package com.planning.platform.dispute.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DisputeReviewerReqDTO {
    @NotNull
    private Long userId;
}
