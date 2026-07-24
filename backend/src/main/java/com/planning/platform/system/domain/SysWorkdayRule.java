package com.planning.platform.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_workday_rule")
public class SysWorkdayRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate ruleDate;
    private String ruleType;
    private Boolean forceReport;
    private String description;
    private Integer status;
    private Integer versionNo;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
