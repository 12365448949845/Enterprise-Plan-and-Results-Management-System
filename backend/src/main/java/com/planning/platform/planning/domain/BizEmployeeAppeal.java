package com.planning.platform.planning.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_employee_appeal")
public class BizEmployeeAppeal {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appealNo;
    private String title;
    private String reason;
    private String status;
    private Long ownerUserId;
    private Long deptId;
    private Long relatedResultId;
    private Long handlerId;
    private String handleComment;
    private LocalDateTime handledAt;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
