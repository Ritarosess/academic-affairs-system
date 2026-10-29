package com.school.edu.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 学业预警指标（由 SQL 聚合直接映射，供预警计算使用，不直接对外暴露）。
 */
@Data
public class WarningMetrics {

    private Long studentId;
    /** 累计不及格学分 */
    private BigDecimal failedCredits;
    /** 累计不及格门数 */
    private Integer failedCourseCount;
    /** 已出成绩的课程门数 */
    private Integer scoredCourseCount;
    /** 已出成绩的学分合计 */
    private BigDecimal scoredCredits;
    /** 已通过的学分合计 */
    private BigDecimal passedCredits;
    /** 单学期最多不及格门数（按学期分组取最大值） */
    private Integer maxSemesterFailedCount;

    public int failedCourseCountSafe() {
        return failedCourseCount == null ? 0 : failedCourseCount;
    }

    public int scoredCourseCountSafe() {
        return scoredCourseCount == null ? 0 : scoredCourseCount;
    }

    public int maxSemesterFailedCountSafe() {
        return maxSemesterFailedCount == null ? 0 : maxSemesterFailedCount;
    }

    public BigDecimal failedCreditsSafe() {
        return failedCredits == null ? BigDecimal.ZERO : failedCredits;
    }

    public BigDecimal scoredCreditsSafe() {
        return scoredCredits == null ? BigDecimal.ZERO : scoredCredits;
    }

    public BigDecimal passedCreditsSafe() {
        return passedCredits == null ? BigDecimal.ZERO : passedCredits;
    }
}
