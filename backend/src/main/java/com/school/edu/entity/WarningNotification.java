package com.school.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学业预警邮件通知记录。冗余保存发送时的等级与指标快照，便于事后核对当时为何判定为预警。
 */
@Data
@TableName("warning_notification")
public class WarningNotification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;
    private Long userId;
    private String studentNo;
    private String studentName;
    private String email;
    private String level;
    private String levelLabel;
    private BigDecimal failedCredits;
    private Integer maxSemesterFailedCount;
    private String subject;
    private String content;
    /** SENT 已发送 / SIMULATED 模拟发送 / FAILED 发送失败 / SKIPPED 无邮箱跳过 */
    private String status;
    private String errorMessage;
    private Long operatorId;
    private LocalDateTime sendTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
