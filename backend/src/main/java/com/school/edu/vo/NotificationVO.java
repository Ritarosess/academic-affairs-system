package com.school.edu.vo;

import java.time.LocalDateTime;

/** 邮件通知历史记录视图对象。 */
public record NotificationVO(
        Long id,
        Long studentId,
        String studentNo,
        String studentName,
        String email,
        String level,
        String levelLabel,
        String subject,
        String status,
        String statusLabel,
        String errorMessage,
        LocalDateTime sendTime) {
}
