package com.planning.platform.planning.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WeekPlanDecisionReqDTO {

    @NotNull(message = "缺少数据版本，请刷新后重试")
    private Integer versionNo;

    @Size(max = 500, message = "审批意见不能超过500个字符")
    private String comment;
}
