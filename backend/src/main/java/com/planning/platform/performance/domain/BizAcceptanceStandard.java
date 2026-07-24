package com.planning.platform.performance.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_acceptance_standard")
public class BizAcceptanceStandard {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private String standardText;
    private Boolean requireReviewPassed;
    private String evidenceRequirement;
    private String versionNo;
    private String status;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
