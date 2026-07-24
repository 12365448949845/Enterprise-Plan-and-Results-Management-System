package com.planning.platform.dispute.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_dispute_reviewer")
public class BizDisputeReviewer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private Long userId;
    private String sourceType;
    private String recusalStatus;
    private String recusalReason;
    private LocalDateTime joinedAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
