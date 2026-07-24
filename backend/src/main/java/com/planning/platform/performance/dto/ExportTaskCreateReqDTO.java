package com.planning.platform.performance.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ExportTaskCreateReqDTO {

    private String dimensionType;
    private String dimensionId;
    private String periodType;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    @NotEmpty(message = "请至少选择一种导出格式")
    private List<String> formats;

    private Boolean includeEvidence;
    private String watermark;
}
