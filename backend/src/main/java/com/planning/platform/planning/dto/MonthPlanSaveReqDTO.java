package com.planning.platform.planning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MonthPlanSaveReqDTO {

    @NotBlank
    @Size(max = 120)
    private String title;

    @NotBlank
    @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])")
    private String planMonth;

    @NotBlank
    private String content;
}
