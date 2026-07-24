package com.planning.platform.planning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ResultSaveReqDTO {

    @NotBlank
    @Size(max = 120)
    private String title;

    @NotNull
    private LocalDate resultDate;

    @NotBlank
    private String content;

    private String planType;
    private Long planId;
    private Boolean temporary;
    private String temporaryReason;
}
