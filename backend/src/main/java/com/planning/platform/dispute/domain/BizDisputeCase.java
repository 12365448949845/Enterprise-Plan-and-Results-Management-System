package com.planning.platform.dispute.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_dispute_case")
public class BizDisputeCase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String caseNo;
    private Long appealId;
    private Long ownerUserId;
    private Long deptId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String disputeSubject;
    private String status;
    private String packageStatus;
    private String packageChecksum;
    private LocalDateTime deadlineAt;
    private Long decidedBy;
    private LocalDateTime decidedAt;
    private String decision;
    private String decisionComment;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
