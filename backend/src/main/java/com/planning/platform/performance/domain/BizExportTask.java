package com.planning.platform.performance.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_export_task")
public class BizExportTask {

    @TableId(type = IdType.INPUT)
    private String id;
    private String dimensionType;
    private String dimensionId;
    private String dimensionName;
    private String periodType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String formats;
    private Boolean includeEvidence;
    private String watermark;
    private String integrityStatus;
    private String missingItems;
    private String checksum;
    private String status;
    private String sizeText;
    private Long requestedBy;
    private String requestedByName;
    private LocalDateTime requestedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime expireAt;
    private String errorMessage;
    private String fileName;
    private String filePath;
    private Long deptId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
