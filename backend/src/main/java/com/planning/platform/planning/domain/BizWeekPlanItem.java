package com.planning.platform.planning.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_week_plan_item")
public class BizWeekPlanItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long weekPlanId;
    private Long monthPlanItemId;
    private String content;
    private String deliverable;
    private String acceptanceStandard;
    private LocalDate plannedFinishDate;
    private Integer sortNo;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
