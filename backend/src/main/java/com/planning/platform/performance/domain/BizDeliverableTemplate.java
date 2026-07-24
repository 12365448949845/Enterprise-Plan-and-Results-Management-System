package com.planning.platform.performance.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_deliverable_template")
public class BizDeliverableTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deptId;
    private String templateName;
    private String evidenceType;
    private Boolean requiredFlag;
    private String appliesTo;
    private String description;
    private String versionNo;
    private String status;
    private Integer referenceCount;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
