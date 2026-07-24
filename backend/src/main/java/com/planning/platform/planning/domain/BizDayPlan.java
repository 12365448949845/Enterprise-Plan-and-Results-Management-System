package com.planning.platform.planning.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_day_plan")
public class BizDayPlan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private LocalDate planDate;
    private String content;
    private String remark;
    private Long monthPlanId;
    private Long monthPlanItemId;
    private Long ownerUserId;
    private Long deptId;
    private String status;
    private LocalDateTime submitAt;
    private Long approverId;
    private LocalDateTime approveAt;
    private String approvalComment;
    private String departmentReviewComment;
    private LocalDateTime approvalDueAt;
    private String reviewStatus;
    private String riskLevel;
    private String aiCheckResult;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
