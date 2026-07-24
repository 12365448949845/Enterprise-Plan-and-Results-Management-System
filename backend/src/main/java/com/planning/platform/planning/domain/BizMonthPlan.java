package com.planning.platform.planning.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_month_plan")
public class BizMonthPlan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String planMonth;
    private String content;
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
