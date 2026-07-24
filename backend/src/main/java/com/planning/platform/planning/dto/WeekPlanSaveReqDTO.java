package com.planning.platform.planning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class WeekPlanSaveReqDTO {

    @NotNull(message = "请选择周计划开始日期")
    private LocalDate weekStart;

    private Integer versionNo;

    @Valid
    @NotEmpty(message = "请至少填写一条周计划")
    @Size(max = 100, message = "周计划条目不能超过100条")
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "请选择关联的月计划条目")
        private Long monthPlanItemId;

        @NotBlank(message = "周计划内容不能为空")
        @Size(max = 5000, message = "周计划内容不能超过5000个字符")
        private String content;

        @Size(max = 500, message = "交付物不能超过500个字符")
        private String deliverable;

        private LocalDate plannedFinishDate;
    }
}
