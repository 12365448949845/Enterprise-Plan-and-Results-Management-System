package com.planning.platform.dispute.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_dispute_opinion")
public class BizDisputeOpinion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private Long reviewerId;
    private String opinion;
    private String comment;
    private Integer versionNo;
    private LocalDateTime submittedAt;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
