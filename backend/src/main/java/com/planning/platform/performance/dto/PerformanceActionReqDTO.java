package com.planning.platform.performance.dto;

import lombok.Data;

@Data
public class PerformanceActionReqDTO {

    private String action;
    private String decision;
    private String comment;
    private String riskLevel;
    private Boolean notifyEmployee;
    private Boolean keepEvidenceChain;
    private String authPassword;
}
