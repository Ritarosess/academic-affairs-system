package com.school.edu.dto;

import jakarta.validation.constraints.NotNull;

/** 选课/退课请求体。 */
public final class SelectionDto {

    private SelectionDto() {
    }

    public record SelectRequest(@NotNull(message = "课程ID不能为空") Long courseId) {
    }
}
