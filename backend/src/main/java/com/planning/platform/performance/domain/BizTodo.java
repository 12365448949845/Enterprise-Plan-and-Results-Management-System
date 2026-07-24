package com.planning.platform.performance.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_todo")
public class BizTodo {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sceneCode;
    private String title;
    private String triggerText;
    private Long receiverId;
    private String receiverName;
    private String objectType;
    private String objectId;
    private LocalDateTime dueAt;
    private String requirementText;
    private String impactText;
    private String messageType;
    private String status;
    private Integer remindCount;
    private String routeHint;
    private LocalDateTime readAt;
    private Long deptId;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
