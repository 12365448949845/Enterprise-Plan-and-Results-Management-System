package com.planning.platform.performance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ScoreRuleSimulateReqDTO {

    private Long employeeId;
    private String employeeName;
    private BigDecimal completionRatio;
    private Integer overdueCount;
    private Integer rejectCount;
    private Boolean evidenceComplete;
    private Boolean reviewPassed;
}
