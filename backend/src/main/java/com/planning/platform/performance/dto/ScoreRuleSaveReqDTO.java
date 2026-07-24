package com.planning.platform.performance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
public class ScoreRuleSaveReqDTO {

    @NotNull(message = "适用组织不能为空")
    private Long orgId;

    @NotBlank(message = "规则名称不能为空")
    @Size(max = 120, message = "规则名称不能超过120个字符")
    private String ruleName;

    private LocalDate effectiveStart;
    private LocalDate effectiveEnd;
    private Map<String, Object> ruleJson;
}
