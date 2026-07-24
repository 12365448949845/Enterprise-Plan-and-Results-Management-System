package com.planning.platform.ai.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExtraTaskPreviewReq {
    @Size(max = 120)
    private String taskName;
    @Size(max = 5000)
    private String taskContent;
    @Size(max = 500)
    private String deliverable;
    private LocalDate deadline;
    @DecimalMin("0.01")
    private BigDecimal performanceWeight;
}
