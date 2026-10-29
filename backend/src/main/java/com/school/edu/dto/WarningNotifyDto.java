package com.school.edu.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 学业预警邮件通知请求体。 */
public final class WarningNotifyDto {

    private WarningNotifyDto() {
    }

    /**
     * @param studentIds 需要发送通知的学生 ID 列表（一次最多 200 人）
     */
    public record NotifyRequest(
            @NotEmpty(message = "请至少选择一名学生")
            @Size(max = 200, message = "单次最多发送 200 人，请分批发送")
            List<Long> studentIds) {
    }
}
