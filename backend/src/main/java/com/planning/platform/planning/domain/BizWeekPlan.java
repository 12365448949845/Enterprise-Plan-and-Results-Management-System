package com.planning.platform.planning.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_week_plan")
public class BizWeekPlan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private Long ownerUserId;
    private Long deptId;
    private String status;
    private Integer versionNo;
    private LocalDateTime submitAt;
    private Long approverId;
    private LocalDateTime approveAt;
    private String approvalComment;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
