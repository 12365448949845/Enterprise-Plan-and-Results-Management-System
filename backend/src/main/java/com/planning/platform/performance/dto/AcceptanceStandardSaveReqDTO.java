package com.planning.platform.performance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AcceptanceStandardSaveReqDTO {

    @NotNull(message = "交付物模板不能为空")
    private Long templateId;

    @NotBlank(message = "验收标准不能为空")
    @Size(max = 2000, message = "验收标准不能超过2000个字符")
    private String standardText;

    private Boolean requireReviewPassed;
    @Size(max = 1000, message = "证据要求不能超过1000个字符")
    private String evidenceRequirement;
}
