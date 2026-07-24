package com.planning.platform.performance.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BatchActionReqDTO {

    @NotEmpty(message = "请选择要处理的数据")
    private List<String> ids;

    private String action;
    private String decision;
    private String comment;
    private String riskLevel;
    private Boolean notifyEmployee;
}
