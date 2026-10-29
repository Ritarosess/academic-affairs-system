package com.school.edu.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 学期与选课周期请求体。 */
public final class SemesterDto {

    private SemesterDto() {
    }

    public record SemesterRequest(
            Long id,
            @NotBlank(message = "学期名称不能为空") String name,
            LocalDate startDate,
            LocalDate endDate,
            /** 选课开始时间，格式 yyyy-MM-dd HH:mm:ss */
            LocalDateTime selectStartTime,
            /** 选课结束时间，格式 yyyy-MM-dd HH:mm:ss */
            LocalDateTime selectEndTime,
            /** 退课截止时间，可空（为空则与选课结束时间一致） */
            LocalDateTime dropEndTime,
            Integer isCurrent) {
    }
}
