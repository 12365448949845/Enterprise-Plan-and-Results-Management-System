package com.planning.platform.planning.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_result_evidence")
public class BizResultEvidence {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resultId;
    private String fileName;
    private String fileUrl;
    private String fileType;
    private String status;
    private Boolean reviewPassed;
    private Long fileSize;
    private String checksum;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Integer deleted;
}
