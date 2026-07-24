package com.planning.platform.planning.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_month_plan_item")
public class BizMonthPlanItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long monthPlanId;
    private String taskType;
    private BigDecimal performanceWeight;
    private String taskName;
    private String taskContent;
    private String target;
    private String progress;
    private String deliverable;
    private String acceptanceStandard;
    private BigDecimal estimatedHours;
    private LocalDate deadline;
    private Integer completionRate;
    private String status;
    private LocalDateTime submitAt;
    private Long approverId;
    private LocalDateTime approveAt;
    private String approvalComment;
    private Integer versionNo;
    private Integer sortNo;
    private String remark;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
