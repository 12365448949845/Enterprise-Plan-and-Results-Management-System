package com.planning.platform.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_ai_review")
public class BizAiReview {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private Long bizId;
    private String bizVersion;
    private String contentHash;
    private Long ownerUserId;
    private Long deptId;
    private String triggerSource;
    private String reviewStatus;
    private String overallRisk;
    private String provider;
    private String modelName;
    private String promptVersion;
    private String resultJson;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
