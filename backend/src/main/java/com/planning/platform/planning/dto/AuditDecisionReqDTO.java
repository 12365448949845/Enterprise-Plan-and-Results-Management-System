package com.planning.platform.planning.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AuditDecisionReqDTO {

    @NotNull
    private Boolean approved;

    private String comment;
}
