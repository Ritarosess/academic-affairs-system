package com.school.edu.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** 成绩录入请求体。 */
public final class ScoreDto {

    private ScoreDto() {
    }

    public record ScoreItem(
            @NotNull(message = "学生ID不能为空") Long studentId,
            @NotNull(message = "平时成绩不能为空")
            @DecimalMin(value = "0", message = "成绩必须在 0 到 100 之间")
            @DecimalMax(value = "100", message = "成绩必须在 0 到 100 之间") BigDecimal usualScore,
            @NotNull(message = "期末成绩不能为空")
            @DecimalMin(value = "0", message = "成绩必须在 0 到 100 之间")
            @DecimalMax(value = "100", message = "成绩必须在 0 到 100 之间") BigDecimal examScore) {
    }

    public record ScoreBatchRequest(
            @NotEmpty(message = "成绩列表不能为空")
            @Valid List<ScoreItem> items) {
    }
}
