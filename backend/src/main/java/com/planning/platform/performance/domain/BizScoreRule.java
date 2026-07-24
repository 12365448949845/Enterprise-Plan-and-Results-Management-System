package com.planning.platform.performance.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_score_rule")
public class BizScoreRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deptId;
    private String ruleName;
    private String status;
    private LocalDate effectiveStart;
    private LocalDate effectiveEnd;
    private String ruleJson;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
