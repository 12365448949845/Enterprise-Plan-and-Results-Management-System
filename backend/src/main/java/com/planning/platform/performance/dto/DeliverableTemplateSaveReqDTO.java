package com.planning.platform.performance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeliverableTemplateSaveReqDTO {

    @NotNull(message = "适用组织不能为空")
    private Long orgId;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 120, message = "模板名称不能超过120个字符")
    private String templateName;

    @NotBlank(message = "证据类型不能为空")
    @Size(max = 50, message = "证据类型不能超过50个字符")
    private String evidenceType;

    @NotNull(message = "必填规则不能为空")
    private Boolean required;

    @NotBlank(message = "适用场景不能为空")
    @Size(max = 100, message = "适用场景不能超过100个字符")
    private String appliesTo;

    @Size(max = 1000, message = "模板说明不能超过1000个字符")
    private String description;
}
