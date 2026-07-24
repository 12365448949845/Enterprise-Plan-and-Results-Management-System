package com.planning.platform.performance.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_plan_adjustment")
public class BizPlanAdjustment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String adjustmentNo;
    private String originalPlanType;
    private Long originalPlanId;
    private String originalPlanNo;
    private String originalWorkContent;
    private String newPlanType;
    private Long newPlanId;
    private String newPlanNo;
    private Long ownerUserId;
    private Long deptId;
    private String adjustmentType;
    private String reason;
    private String impactText;
    private String operationComment;
    private String status;
    private Boolean keepEvidenceChain;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime operatedAt;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
