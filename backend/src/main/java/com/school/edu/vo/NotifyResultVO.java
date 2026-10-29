package com.school.edu.vo;

import java.util.List;

/** 学业预警邮件通知结果。 */
public final class NotifyResultVO {

    private NotifyResultVO() {
    }

    /**
     * 单个学生的发送结果。
     *
     * @param status SENT 已发送 / SIMULATED 模拟发送 / FAILED 发送失败 / SKIPPED 无邮箱跳过
     */
    public record Item(
            Long studentId,
            String studentNo,
            String studentName,
            String email,
            String level,
            String levelLabel,
            String status,
            String statusLabel,
            String subject,
            String message) {
    }

    /** 本次发送的汇总。 */
    public record Summary(
            int total,
            int sent,
            int simulated,
            int failed,
            int skipped,
            /** 当前是否为真实发送模式（false 表示模拟发送） */
            boolean mailEnabled,
            List<Item> results) {
    }
}
