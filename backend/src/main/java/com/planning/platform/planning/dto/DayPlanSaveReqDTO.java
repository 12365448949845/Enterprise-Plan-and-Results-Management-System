package com.planning.platform.planning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class DayPlanSaveReqDTO {

    @NotBlank
    @Size(max = 120)
    private String title;

    @NotNull
    private LocalDate planDate;

    @NotBlank
    private String content;

    private Long monthPlanId;
}
