package com.planning.platform.planning.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_result")
public class BizResult {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private LocalDate resultDate;
    private String content;
    private Integer completionRate;
    private String versionNo;
    private String planType;
    private Long planId;
    private Long monthPlanItemId;
    private Boolean temporary;
    private String temporaryReason;
    private Long ownerUserId;
    private Long deptId;
    private String status;
    private LocalDateTime submitAt;
    private Long confirmerId;
    private LocalDateTime confirmAt;
    private String confirmComment;
    private String evidenceStatus;
    private String autoLevel;
    private String issueCodes;
    private String issueText;
    private String suggestionStatus;
    private String leaderSuggestion;
    private Long suggestedBy;
    private LocalDateTime suggestedAt;
    private String verifyRecordId;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
